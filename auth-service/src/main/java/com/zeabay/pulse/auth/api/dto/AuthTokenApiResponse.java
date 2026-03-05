package com.zeabay.pulse.auth.api.dto;

public record AuthTokenApiResponse(String accessToken, String refreshToken, Integer expiresIn) {}
