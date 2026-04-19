package com.zeabay.pulse.profile.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Response containing the presigned upload URL and object key")
public record AvatarUploadApiResponse(
    @Schema(
            description = "Presigned URL for direct upload to MinIO",
            example = "http://localhost:9000/pulse-avatars/avatars/...?X-Amz-Signature=...")
        String uploadUrl,
    @Schema(
            description = "Object key to reference in profile update",
            example = "avatars/uuid/img.jpg")
        String objectKey) {}
