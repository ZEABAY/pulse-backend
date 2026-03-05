package com.zeabay.pulse.auth.application.dto;

public record LoginCommand(String usernameOrEmail, String password) {}
