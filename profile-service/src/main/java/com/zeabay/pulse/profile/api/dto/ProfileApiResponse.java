package com.zeabay.pulse.profile.api.dto;

import com.zeabay.pulse.profile.domain.model.Gender;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDate;

@Schema(description = "Full profile response for the authenticated user")
public record ProfileApiResponse(
    String username,
    String firstName,
    String lastName,
    LocalDate dateOfBirth,
    String phoneNumber,
    String avatarUrl,
    String bio,
    Gender gender,
    String location,
    boolean profileCompleted) {}
