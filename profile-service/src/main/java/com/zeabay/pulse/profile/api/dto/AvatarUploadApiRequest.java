package com.zeabay.pulse.profile.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;

@Schema(description = "Request body for obtaining an avatar upload presigned URL")
public record AvatarUploadApiRequest(
    @Schema(description = "Original file name", example = "photo.jpg") @NotBlank String fileName,
    @Schema(
            description = "MIME type of the file",
            example = "image/jpeg",
            allowableValues = {"image/jpeg", "image/png", "image/webp"})
        @NotBlank
        String contentType,
    @Schema(description = "File size in bytes", example = "2048576") @Positive long fileSize) {}
