package com.zeabay.pulse.profile.application.dto;

import com.zeabay.pulse.profile.domain.model.Gender;
import java.time.LocalDate;

/** Command for completing a user profile initially. All required fields must be present. */
public record CompleteProfileCommand(
    String firstName,
    String lastName,
    LocalDate dateOfBirth,
    String phoneNumber,
    String bio,
    Gender gender,
    String location,
    String avatarKey) {}
