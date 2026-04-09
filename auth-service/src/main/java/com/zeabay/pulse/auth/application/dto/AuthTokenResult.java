package com.zeabay.pulse.auth.application.dto;

public record AuthTokenResult(
    String accessToken, String refreshToken, Integer expiresIn, Integer refreshExpiresIn) {}
