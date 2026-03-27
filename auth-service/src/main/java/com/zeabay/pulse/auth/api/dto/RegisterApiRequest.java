package com.zeabay.pulse.auth.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

@Schema(description = "Request body for user registration")
public record RegisterApiRequest(
    @Schema(
            description =
                "Username (letters, digits, underscores and periods)."
                    + " Must not start/end with a period or contain consecutive periods.",
            example = "john_doe",
            minLength = 3,
            maxLength = 30)
        @NotBlank(message = "Username is required")
        @Size(min = 3, max = 30, message = "Username must be between 3 and 30 characters")
        @Pattern(
            regexp = "^(?![.])[a-zA-Z0-9_]+(?:\\.[a-zA-Z0-9_]+)*$",
            message =
                "Username can only contain letters, digits, underscores and periods."
                    + " It must not start/end with a period or contain consecutive periods")
        String username,
    @Schema(description = "Valid email address", example = "john@example.com")
        @NotBlank(message = "Email is required")
        @Email(message = "Email should be valid")
        String email,
    @Schema(
            description =
                "Password (8-100 chars). Must contain at least one uppercase letter,"
                    + " one lowercase letter, one digit, and one special character.",
            example = "MyP@ss1234",
            minLength = 8,
            maxLength = 100)
        @NotBlank(message = "Password is required")
        @Size(min = 8, max = 100, message = "Password must be between 8 and 100 characters")
        @Pattern(
            regexp =
                "^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d)(?=.*[!@#$%^&*()_+\\-=\\[\\]{}|;:'\",.<>?/`~])"
                    + "[A-Za-z\\d!@#$%^&*()_+\\-=\\[\\]{}|;:'\",.<>?/`~]{8,100}$",
            message =
                "Password must contain at least one uppercase letter, one lowercase letter,"
                    + " one digit, and one special character")
        String password) {

  @Override
  public String toString() {
    return "RegisterApiRequest[username=" + username + ", email=" + email + ", password=***]";
  }
}
