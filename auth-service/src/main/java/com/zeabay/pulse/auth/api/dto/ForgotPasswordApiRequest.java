package com.zeabay.pulse.auth.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@Schema(description = "Request payload for initiating a password reset")
public record ForgotPasswordApiRequest(
    @Schema(
            description = "Email address of the user requesting a password reset",
            example = "john.doe@example.com",
            requiredMode = Schema.RequiredMode.REQUIRED,
            maxLength = 254)
        @NotBlank
        @Email
        @Size(max = 254)
        String email) {}
