package com.zeabay.pulse.profile.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Public profile response (sensitive fields excluded)")
public record PublicProfileApiResponse(
    String username,
    String firstName,
    String lastName,
    String avatarUrl,
    String bio,
    String location) {}
