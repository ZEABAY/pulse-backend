package com.zeabay.pulse.auth.application.dto;

/** Application-layer command for user login. */
public record LoginCommand(String usernameOrEmail, String password) {}
