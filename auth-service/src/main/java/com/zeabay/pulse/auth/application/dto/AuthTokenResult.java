package com.zeabay.pulse.auth.application.dto;

/** Application-layer DTO carrying OAuth2 token details from the identity provider. */
public record AuthTokenResult(
    String accessToken, String refreshToken, Integer expiresIn, Integer refreshExpiresIn) {}
