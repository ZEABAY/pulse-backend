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
        @NotBlank
        @Size(min = 3, max = 30)
        @Pattern(regexp = "^(?![.])[a-zA-Z0-9ÇĞİÖŞÜçğıöşü_]+(?:\\.[a-zA-Z0-9ÇĞİÖŞÜçğıöşü_]+)*$")
        String username,
    @Schema(description = "Valid email address", example = "john@example.com") @NotBlank @Email
        String email,
    @Schema(
            description =
                "Password (8-100 chars). Must contain at least one uppercase letter,"
                    + " one lowercase letter, one digit, and one special character.",
            example = "MyP@ss1234",
            minLength = 8,
            maxLength = 100)
        @NotBlank
        @Size(min = 8, max = 100)
        @Pattern(
            regexp =
                "^(?=.*[a-zçğıöşü])(?=.*[A-ZÇĞİÖŞÜ])(?=.*\\d)(?=.*[!@#$%^&*()_+\\-=\\[\\]{}|;:'\",.<>?/`~])"
                    + "[A-Za-zÇĞİÖŞÜçğıöşü\\d!@#$%^&*()_+\\-=\\[\\]{}|;:'\",.<>?/`~]{8,100}$")
        String password) {

  @Override
  public String toString() {
    return "RegisterApiRequest[username=" + username + ", email=" + email + ", password=***]";
  }
}
