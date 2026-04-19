package com.zeabay.pulse.profile.api.mapper;

import com.zeabay.pulse.profile.api.dto.AvatarUploadApiRequest;
import com.zeabay.pulse.profile.api.dto.AvatarUploadApiResponse;
import com.zeabay.pulse.profile.api.dto.CompleteProfileApiRequest;
import com.zeabay.pulse.profile.api.dto.ProfileApiResponse;
import com.zeabay.pulse.profile.api.dto.PublicProfileApiResponse;
import com.zeabay.pulse.profile.api.dto.UpdateProfileApiRequest;
import com.zeabay.pulse.profile.application.dto.AvatarUploadCommand;
import com.zeabay.pulse.profile.application.dto.AvatarUploadResult;
import com.zeabay.pulse.profile.application.dto.CompleteProfileCommand;
import com.zeabay.pulse.profile.application.dto.UpdateProfileCommand;
import com.zeabay.pulse.profile.application.port.out.ObjectStoragePort;
import com.zeabay.pulse.profile.domain.model.UserProfile;
import org.mapstruct.AfterMapping;
import org.mapstruct.Mapper;
import org.mapstruct.MappingTarget;
import org.mapstruct.NullValuePropertyMappingStrategy;
import org.mapstruct.ReportingPolicy;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * MapStruct mapper for converting between API DTOs, application-layer commands, and domain
 * entities.
 */
@Mapper(
    componentModel = "spring",
    unmappedTargetPolicy = ReportingPolicy.IGNORE,
    nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
public abstract class ProfileMapper {

  @Autowired protected ObjectStoragePort objectStoragePort;

  public abstract CompleteProfileCommand toCompleteCommand(CompleteProfileApiRequest request);

  public abstract UpdateProfileCommand toUpdateCommand(UpdateProfileApiRequest request);

  public abstract AvatarUploadCommand toAvatarCommand(AvatarUploadApiRequest request);

  public abstract ProfileApiResponse toProfileResponse(UserProfile profile);

  public abstract PublicProfileApiResponse toPublicProfileResponse(UserProfile profile);

  public abstract AvatarUploadApiResponse toAvatarResponse(AvatarUploadResult result);

  /** Updates an existing {@link UserProfile} from a command. Null optional fields are ignored. */
  public abstract void updateProfileFromCommand(
      UpdateProfileCommand command, @MappingTarget UserProfile profile);

  public abstract void completeProfileFromCommand(
      CompleteProfileCommand command, @MappingTarget UserProfile profile);

  /** Sets avatarUrl from S3 after MapStruct has mapped the simple fields. */
  @AfterMapping
  protected void setAvatarFieldsForComplete(
      CompleteProfileCommand command, @MappingTarget UserProfile profile) {
    if (command.avatarKey() != null && !command.avatarKey().isBlank()) {
      profile.setAvatarKey(command.avatarKey());
      profile.setAvatarUrl(objectStoragePort.buildPublicUrl(command.avatarKey()));
    }
  }

  /** Sets avatarUrl from S3 after MapStruct has mapped the simple fields. */
  @AfterMapping
  protected void setAvatarFieldsForUpdate(
      UpdateProfileCommand command, @MappingTarget UserProfile profile) {
    if (command.avatarKey() != null && !command.avatarKey().isBlank()) {
      profile.setAvatarKey(command.avatarKey());
      profile.setAvatarUrl(objectStoragePort.buildPublicUrl(command.avatarKey()));
    }
  }
}
