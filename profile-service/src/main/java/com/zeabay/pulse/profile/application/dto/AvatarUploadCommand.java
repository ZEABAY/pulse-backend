package com.zeabay.pulse.profile.application.dto;

/** Command for requesting an avatar upload presigned URL. */
public record AvatarUploadCommand(String fileName, String contentType, long fileSize) {}
