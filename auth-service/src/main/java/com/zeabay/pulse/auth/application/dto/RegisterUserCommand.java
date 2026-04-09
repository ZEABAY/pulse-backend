package com.zeabay.pulse.auth.application.dto;

/** Application-layer command for new user registration. */
public record RegisterUserCommand(String username, String email, String password) {}
