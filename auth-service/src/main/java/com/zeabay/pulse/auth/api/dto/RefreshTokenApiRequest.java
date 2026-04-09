package com.zeabay.pulse.auth.api.dto;

import jakarta.validation.constraints.NotBlank;

/** Request body for POST /api/v1/auth/refresh. */
public record RefreshTokenApiRequest(@NotBlank String refreshToken) {}
