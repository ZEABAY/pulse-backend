package com.zeabay.pulse.profile.application.usecase;

import com.zeabay.pulse.profile.application.dto.AvatarUploadCommand;
import com.zeabay.pulse.profile.application.dto.AvatarUploadResult;
import com.zeabay.pulse.profile.application.dto.CompleteProfileCommand;
import com.zeabay.pulse.profile.application.dto.UpdateProfileCommand;
import com.zeabay.pulse.profile.domain.model.UserProfile;
import reactor.core.publisher.Mono;

/** Primary use case interface for all profile operations. */
public interface ProfileService {

  /**
   * Retrieves the authenticated user's profile.
   *
   * @param keycloakId the user's Keycloak ID
   * @return the user profile
   */
  Mono<UserProfile> getMyProfile(String keycloakId);

  /**
   * Completes the user's profile during onboarding.
   *
   * @param keycloakId the user's Keycloak ID
   * @param command the required fields to complete the profile
   * @return the updated, completed user profile
   */
  Mono<UserProfile> completeProfile(String keycloakId, CompleteProfileCommand command);

  /**
   * Partially updates an existing user profile.
   *
   * @param keycloakId the user's Keycloak ID
   * @param command the optional fields to update
   * @return the updated user profile
   */
  Mono<UserProfile> updateProfile(String keycloakId, UpdateProfileCommand command);

  /**
   * Retrieves a user's public profile by their username.
   *
   * @param username the target username
   * @return the public user profile
   */
  Mono<UserProfile> getPublicProfile(String username);

  /**
   * Generates a presigned URL to allow direct client upload of an avatar to object storage.
   *
   * @param keycloakId the user's Keycloak ID
   * @param command metadata about the file to be uploaded
   * @return upload result containing the presigned URL and the future object key
   */
  Mono<AvatarUploadResult> generateAvatarUploadUrl(String keycloakId, AvatarUploadCommand command);

  /**
   * Deletes the user's current avatar from object storage.
   *
   * @param keycloakId the user's Keycloak ID
   */
  Mono<Void> deleteAvatar(String keycloakId);
}
