package com.zeabay.pulse.auth.api.dto;

/** API response body containing OAuth2 token details returned to the client. */
public record AuthTokenApiResponse(
    String accessToken, String refreshToken, Integer expiresIn, Integer refreshExpiresIn) {}
