package com.zeabay.pulse.profile.application.dto;

import com.zeabay.pulse.profile.domain.model.Gender;
import java.time.LocalDate;

/** Command for creating or updating a user profile. */
public record UpdateProfileCommand(
    String firstName,
    String lastName,
    LocalDate dateOfBirth,
    String phoneNumber,
    String bio,
    Gender gender,
    String location,
    String avatarKey) {}
