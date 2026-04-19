package com.zeabay.pulse.profile.application.dto;

/** Result containing the presigned upload URL and object key for avatar upload. */
public record AvatarUploadResult(String uploadUrl, String objectKey) {}
