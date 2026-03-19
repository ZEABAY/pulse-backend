package com.zeabay.pulse.auth.application.dto;

public record RegisterUserCommand(String username, String email, String password) {}
