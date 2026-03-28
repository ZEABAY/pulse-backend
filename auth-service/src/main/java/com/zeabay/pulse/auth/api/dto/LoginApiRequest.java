package com.zeabay.pulse.auth.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

@Schema(description = "Request body for user login")
public record LoginApiRequest(
    @Schema(description = "Username or email address", example = "john_doe", maxLength = 254)
        @NotBlank
        @Size(max = 254)
        String username,
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
                "^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d)(?=.*[!@#$%^&*()_+\\-=\\[\\]{}|;:'\",.<>?/`~])"
                    + "[A-Za-z\\d!@#$%^&*()_+\\-=\\[\\]{}|;:'\",.<>?/`~]{8,100}$")
        String password) {

  @Override
  public String toString() {
    return "LoginApiRequest[username=" + username + ", password=***]";
  }
}
