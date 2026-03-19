package com.zeabay.pulse.auth.api.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Request body for POST /api/v1/auth/verify.
 *
 * <p>Both {@code email} and {@code token} are required to prevent OTP collision attacks — a 6-digit
 * token alone is not globally unique, so we verify it is bound to the expected user.
 */
public record VerifyEmailApiRequest(
    @NotBlank(message = "Email is required") @Email(message = "Email should be valid") String email,
    @NotBlank(message = "Token is required")
        @Size(min = 6, max = 6, message = "Token must be 6 digits")
        @Pattern(regexp = "\\d{6}", message = "Token must be 6 numeric digits")
        String token) {}
