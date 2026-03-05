package com.zeabay.pulse.auth.api.dto;

import jakarta.validation.constraints.NotBlank;

public record RefreshTokenApiRequest(@NotBlank String refreshToken) {}
