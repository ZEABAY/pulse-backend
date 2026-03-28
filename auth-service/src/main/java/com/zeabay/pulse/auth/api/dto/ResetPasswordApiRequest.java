package com.zeabay.pulse.auth.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

@Schema(description = "Request payload for resetting a password using a token")
public record ResetPasswordApiRequest(
    @Schema(
            description = "Email address of the user",
            example = "john.doe@example.com",
            requiredMode = Schema.RequiredMode.REQUIRED,
            maxLength = 254)
        @NotBlank
        @Email
        @Size(max = 254)
        String email,
    @Schema(
            description = "6-digit OTP received via email",
            example = "123456",
            requiredMode = Schema.RequiredMode.REQUIRED,
            minLength = 6,
            maxLength = 6)
        @NotBlank
        @Size(min = 6, max = 6)
        @Pattern(regexp = "^\\d{6}$")
        String otp,
    @Schema(
            description =
                "New password. Must contain at least one uppercase letter, "
                    + "one lowercase letter, one digit, and one special character.",
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
    return "ResetPasswordApiRequest[email=" + email + ", otp=***, password=***]";
  }
}
