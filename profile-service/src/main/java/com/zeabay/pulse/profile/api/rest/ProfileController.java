package com.zeabay.pulse.profile.api.rest;

import com.zeabay.common.api.model.ZeabayApiResponse;
import com.zeabay.common.logging.Loggable;
import com.zeabay.common.web.ZeabayResponses;
import com.zeabay.pulse.profile.api.dto.AvatarUploadApiRequest;
import com.zeabay.pulse.profile.api.dto.AvatarUploadApiResponse;
import com.zeabay.pulse.profile.api.dto.CompleteProfileApiRequest;
import com.zeabay.pulse.profile.api.dto.ProfileApiResponse;
import com.zeabay.pulse.profile.api.dto.PublicProfileApiResponse;
import com.zeabay.pulse.profile.api.dto.UpdateProfileApiRequest;
import com.zeabay.pulse.profile.api.mapper.ProfileMapper;
import com.zeabay.pulse.profile.application.usecase.ProfileService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

/**
 * REST controller for user profile operations: retrieval, update, avatar management.
 *
 * <p>All endpoints except public profile require a valid JWT. The {@code keycloakId} is extracted
 * from the JWT {@code sub} claim. All endpoints return a standardized {@link ZeabayApiResponse}
 * envelope.
 */
@Loggable
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/profiles")
@Tag(name = "Profile", description = "User profile management endpoints")
@SecurityRequirement(name = "bearerAuth")
public class ProfileController {

  private final ProfileService profileService;
  private final ProfileMapper profileMapper;

  @Operation(
      summary = "Get my profile",
      description = "Returns the authenticated user's full profile including completion status.")
  @ApiResponses({
    @ApiResponse(responseCode = "200", description = "Profile retrieved successfully"),
    @ApiResponse(responseCode = "404", description = "Profile not found")
  })
  @GetMapping("/me")
  public Mono<ZeabayApiResponse<ProfileApiResponse>> getMyProfile(
      Mono<JwtAuthenticationToken> auth) {
    return auth.flatMap(
        jwt ->
            profileService
                .getMyProfile(jwt.getName())
                .map(profileMapper::toProfileResponse)
                .flatMap(ZeabayResponses::ok));
  }

  @Operation(
      summary = "Complete my profile",
      description =
          "Completes the authenticated user's profile initially. "
              + "Sets profileCompleted=true. All required fields must be provided.")
  @ApiResponses({
    @ApiResponse(responseCode = "200", description = "Profile completed successfully"),
    @ApiResponse(responseCode = "400", description = "Invalid request payload"),
    @ApiResponse(responseCode = "404", description = "Profile not found")
  })
  @PostMapping("/me/complete")
  public Mono<ZeabayApiResponse<ProfileApiResponse>> completeProfile(
      Mono<JwtAuthenticationToken> auth, @Valid @RequestBody CompleteProfileApiRequest request) {
    return auth.flatMap(
        jwt ->
            profileService
                .completeProfile(jwt.getName(), profileMapper.toCompleteCommand(request))
                .map(profileMapper::toProfileResponse)
                .flatMap(ZeabayResponses::ok));
  }

  @Operation(
      summary = "Update my profile",
      description =
          "Partially updates the authenticated user's profile. "
              + "Only provided fields are modified.")
  @ApiResponses({
    @ApiResponse(responseCode = "200", description = "Profile updated successfully"),
    @ApiResponse(responseCode = "400", description = "Invalid request payload"),
    @ApiResponse(responseCode = "404", description = "Profile not found")
  })
  @PatchMapping("/me")
  public Mono<ZeabayApiResponse<ProfileApiResponse>> updateProfile(
      Mono<JwtAuthenticationToken> auth, @Valid @RequestBody UpdateProfileApiRequest request) {
    return auth.flatMap(
        jwt ->
            profileService
                .updateProfile(jwt.getName(), profileMapper.toUpdateCommand(request))
                .map(profileMapper::toProfileResponse)
                .flatMap(ZeabayResponses::ok));
  }

  @Operation(
      summary = "Get avatar upload URL",
      description =
          "Generates a presigned URL for direct upload to object storage. "
              + "The client should PUT the file directly to the returned URL, "
              + "then include the objectKey in the profile update request.")
  @ApiResponses({
    @ApiResponse(responseCode = "200", description = "Upload URL generated successfully"),
    @ApiResponse(responseCode = "400", description = "Invalid file type or size")
  })
  @PostMapping("/me/avatar")
  public Mono<ZeabayApiResponse<AvatarUploadApiResponse>> getAvatarUploadUrl(
      Mono<JwtAuthenticationToken> auth, @Valid @RequestBody AvatarUploadApiRequest request) {
    return auth.flatMap(
        jwt ->
            profileService
                .generateAvatarUploadUrl(jwt.getName(), profileMapper.toAvatarCommand(request))
                .map(profileMapper::toAvatarResponse)
                .flatMap(ZeabayResponses::ok));
  }

  @Operation(
      summary = "Delete my avatar",
      description = "Removes the avatar from object storage and clears the profile reference.")
  @ApiResponses({
    @ApiResponse(responseCode = "200", description = "Avatar deleted successfully"),
    @ApiResponse(responseCode = "404", description = "Profile not found")
  })
  @DeleteMapping("/me/avatar")
  public Mono<ZeabayApiResponse<String>> deleteAvatar(Mono<JwtAuthenticationToken> auth) {
    return auth.flatMap(
        jwt ->
            profileService
                .deleteAvatar(jwt.getName())
                .then(ZeabayResponses.ok("Avatar deleted successfully")));
  }

  @Operation(
      summary = "Get public profile",
      description =
          "Returns a public view of another user's profile. "
              + "Sensitive fields (dateOfBirth, phoneNumber, gender) are excluded.")
  @ApiResponses({
    @ApiResponse(responseCode = "200", description = "Public profile retrieved"),
    @ApiResponse(responseCode = "404", description = "Profile not found or not completed")
  })
  @GetMapping("/{username}")
  public Mono<ZeabayApiResponse<PublicProfileApiResponse>> getPublicProfile(
      @PathVariable String username) {
    return profileService
        .getPublicProfile(username)
        .map(profileMapper::toPublicProfileResponse)
        .flatMap(ZeabayResponses::ok);
  }
}
