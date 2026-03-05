package com.zeabay.pulse.auth.api.dto;

import jakarta.validation.constraints.NotBlank;

public record LoginApiRequest(
    @NotBlank(message = "Username or Email is required") String username,
    @NotBlank(message = "Password is required") String password) {

  @Override
  public String toString() {
    return "LoginApiRequest[username=" + username + ", password=***]";
  }
}
