package com.zeabay.pulse.auth.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record LoginApiRequest(
    @NotBlank(message = "Username or Email is required")
        @Size(max = 254, message = "Username or email must not exceed 254 characters")
        String username,
    @NotBlank(message = "Password is required")
        @Size(max = 100, message = "Password must not exceed 100 characters")
        String password) {

  @Override
  public String toString() {
    return "LoginApiRequest[username=" + username + ", password=***]";
  }
}
