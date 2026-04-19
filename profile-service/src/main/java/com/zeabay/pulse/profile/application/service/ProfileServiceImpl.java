package com.zeabay.pulse.profile.application.service;

import com.zeabay.common.api.exception.BusinessException;
import com.zeabay.common.api.exception.ErrorCode;
import com.zeabay.common.logging.Loggable;
import com.zeabay.common.s3.ZeabayS3Properties;
import com.zeabay.pulse.profile.api.mapper.ProfileMapper;
import com.zeabay.pulse.profile.application.dto.AvatarUploadCommand;
import com.zeabay.pulse.profile.application.dto.AvatarUploadResult;
import com.zeabay.pulse.profile.application.dto.CompleteProfileCommand;
import com.zeabay.pulse.profile.application.dto.UpdateProfileCommand;
import com.zeabay.pulse.profile.application.port.out.ObjectStoragePort;
import com.zeabay.pulse.profile.application.port.out.ProfileCachePort;
import com.zeabay.pulse.profile.application.usecase.ProfileService;
import com.zeabay.pulse.profile.domain.model.UserProfile;
import com.zeabay.pulse.profile.domain.repository.UserProfileRepository;
import java.time.Instant;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import reactor.core.publisher.Mono;

/**
 * Core profile service implementing user profile CRUD, avatar management, and caching.
 *
 * <p>Profiles are cached in Redis with key {@code zeabay:profile:{keycloakId}} for read-heavy
 * access patterns. Cache is invalidated on every write operation.
 */
@Slf4j
@Service
@Loggable
@RequiredArgsConstructor
public class ProfileServiceImpl implements ProfileService {

  private static final long BYTES_PER_MB = 1_048_576L;

  private final UserProfileRepository profileRepository;
  private final ObjectStoragePort objectStoragePort;
  private final ProfileCachePort cachePort;
  private final ZeabayS3Properties s3Properties;
  private final ProfileMapper profileMapper;

  @Override
  public Mono<UserProfile> getMyProfile(String keycloakId) {
    return cachePort
        .get(keycloakId)
        .switchIfEmpty(
            profileRepository
                .findByKeycloakId(keycloakId)
                .switchIfEmpty(
                    Mono.error(
                        new BusinessException(
                            ErrorCode.PROFILE_NOT_FOUND,
                            "No profile exists for keycloakId='"
                                + keycloakId
                                + "'. The user-verified event may not have been processed yet.")))
                .flatMap(profile -> cachePort.put(keycloakId, profile).thenReturn(profile)));
  }

  @Override
  @Transactional
  public Mono<UserProfile> completeProfile(String keycloakId, CompleteProfileCommand command) {
    return profileRepository
        .findByKeycloakId(keycloakId)
        .switchIfEmpty(
            Mono.error(
                new BusinessException(
                    ErrorCode.PROFILE_NOT_FOUND,
                    "Cannot complete profile: no skeleton exists for keycloakId='"
                        + keycloakId
                        + "'. Ensure the email verification event has been processed.")))
        .flatMap(
            profile -> {
              validateAvatarKeyOwnership(keycloakId, command.avatarKey());
              String oldAvatarKey = captureOldAvatarKey(profile, command.avatarKey());
              profileMapper.completeProfileFromCommand(command, profile);
              profile.setProfileCompleted(true);
              profile.setUpdatedAt(Instant.now());
              return profileRepository
                  .save(profile)
                  .flatMap(saved -> deleteOldAvatarIfNeeded(oldAvatarKey).thenReturn(saved));
            })
        .flatMap(
            saved ->
                cachePort
                    .evict(keycloakId)
                    .then(cachePort.evict("username:" + saved.getUsername()))
                    .thenReturn(saved));
  }

  @Override
  @Transactional
  public Mono<UserProfile> updateProfile(String keycloakId, UpdateProfileCommand command) {
    return profileRepository
        .findByKeycloakId(keycloakId)
        .switchIfEmpty(
            Mono.error(
                new BusinessException(
                    ErrorCode.PROFILE_NOT_FOUND,
                    "Cannot update profile: no profile found for keycloakId='"
                        + keycloakId
                        + "'.")))
        .flatMap(
            profile -> {
              validateAvatarKeyOwnership(keycloakId, command.avatarKey());
              String oldAvatarKey = captureOldAvatarKey(profile, command.avatarKey());
              profileMapper.updateProfileFromCommand(command, profile);
              profile.setProfileCompleted(true);
              profile.setUpdatedAt(Instant.now());
              return profileRepository
                  .save(profile)
                  .flatMap(saved -> deleteOldAvatarIfNeeded(oldAvatarKey).thenReturn(saved));
            })
        .flatMap(
            saved ->
                cachePort
                    .evict(keycloakId)
                    .then(cachePort.evict("username:" + saved.getUsername()))
                    .thenReturn(saved));
  }

  @Override
  public Mono<UserProfile> getPublicProfile(String username) {
    return cachePort
        .get("username:" + username)
        .switchIfEmpty(
            profileRepository
                .findByUsername(username)
                .switchIfEmpty(
                    Mono.error(
                        new BusinessException(
                            ErrorCode.PROFILE_NOT_FOUND,
                            "Public profile not found for username='" + username + "'.")))
                .filter(UserProfile::isProfileCompleted)
                .switchIfEmpty(
                    Mono.error(
                        new BusinessException(
                            ErrorCode.PROFILE_NOT_COMPLETED,
                            "Profile for username='"
                                + username
                                + "' exists but has not been completed yet.")))
                .flatMap(
                    profile -> cachePort.put("username:" + username, profile).thenReturn(profile)));
  }

  @Override
  public Mono<AvatarUploadResult> generateAvatarUploadUrl(
      String keycloakId, AvatarUploadCommand command) {
    validateAvatarUpload(command);

    String extension = extractExtension(command.fileName());
    String objectKey = "avatars/" + keycloakId + "/" + UUID.randomUUID() + "." + extension;

    return objectStoragePort
        .generatePresignedUploadUrl(objectKey, command.contentType())
        .map(url -> new AvatarUploadResult(url, objectKey));
  }

  @Override
  @Transactional
  public Mono<Void> deleteAvatar(String keycloakId) {
    return profileRepository
        .findByKeycloakId(keycloakId)
        .switchIfEmpty(
            Mono.error(
                new BusinessException(
                    ErrorCode.PROFILE_NOT_FOUND,
                    "Cannot delete avatar: no profile found for keycloakId='" + keycloakId + "'.")))
        .flatMap(
            profile -> {
              if (profile.getAvatarKey() == null || profile.getAvatarKey().isBlank()) {
                return Mono.empty();
              }
              String oldKey = profile.getAvatarKey();
              profile.setAvatarUrl(null);
              profile.setAvatarKey(null);
              profile.setUpdatedAt(Instant.now());
              return profileRepository
                  .save(profile)
                  .then(objectStoragePort.deleteObject(oldKey))
                  .then(cachePort.evict(keycloakId));
            });
  }

  /**
   * Captures the old avatar key before mutation — returns it only if a new avatar is being set and
   * it differs from the old one. Returns {@code null} if no cleanup is needed.
   */
  private String captureOldAvatarKey(UserProfile profile, String newAvatarKey) {
    if (newAvatarKey == null || newAvatarKey.isBlank()) {
      return null;
    }
    String oldKey = profile.getAvatarKey();
    if (oldKey != null && !oldKey.isBlank() && !oldKey.equals(newAvatarKey)) {
      return oldKey;
    }
    return null;
  }

  /** Ensures that users can only assign avatar keys that belong to their own directory. */
  private void validateAvatarKeyOwnership(String keycloakId, String avatarKey) {
    if (avatarKey != null && !avatarKey.isBlank()) {
      String expectedPrefix = "avatars/" + keycloakId + "/";
      if (!avatarKey.startsWith(expectedPrefix)) {
        throw new BusinessException(
            ErrorCode.AVATAR_INVALID_OWNERSHIP,
            "Invalid avatar key ownership: key='"
                + avatarKey
                + "' does not match expected prefix 'avatars/"
                + keycloakId
                + "/'");
      }
    }
  }

  /** Best-effort deletion of old avatar from object storage. Errors are logged, not propagated. */
  private Mono<Void> deleteOldAvatarIfNeeded(String oldAvatarKey) {
    if (oldAvatarKey == null) {
      return Mono.empty();
    }
    return objectStoragePort
        .deleteObject(oldAvatarKey)
        .doOnError(
            e -> log.warn("Failed to delete old avatar key={}: {}", oldAvatarKey, e.getMessage()))
        .onErrorComplete();
  }

  private void validateAvatarUpload(AvatarUploadCommand command) {
    if (!s3Properties.getAllowedContentTypes().contains(command.contentType())) {
      throw new BusinessException(
          ErrorCode.AVATAR_UNSUPPORTED_TYPE,
          "Unsupported file type '"
              + command.contentType()
              + "'. Allowed: "
              + s3Properties.getAllowedContentTypes());
    }
    long maxFileSizeMb = s3Properties.getMaxFileSize() / BYTES_PER_MB;
    if (command.fileSize() > s3Properties.getMaxFileSize()) {
      throw new BusinessException(
          ErrorCode.AVATAR_FILE_TOO_LARGE,
          "File size " + command.fileSize() + " bytes exceeds maximum of " + maxFileSizeMb + " MB");
    }
  }

  private String extractExtension(String fileName) {
    if (fileName == null || !fileName.contains(".")) {
      return "jpg";
    }
    return fileName.substring(fileName.lastIndexOf('.') + 1).toLowerCase();
  }
}
