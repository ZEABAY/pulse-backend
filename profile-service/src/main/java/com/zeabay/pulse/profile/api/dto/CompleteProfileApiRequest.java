package com.zeabay.pulse.profile.api.dto;

import com.zeabay.pulse.profile.domain.model.Gender;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Past;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;

@Schema(
    description =
        "Request body for completing a user profile initially. Required fields must be provided.")
public record CompleteProfileApiRequest(
    @Schema(description = "First name", example = "John", minLength = 2, maxLength = 50)
        @NotBlank
        @Size(min = 2, max = 50)
        String firstName,
    @Schema(description = "Last name", example = "Doe", minLength = 2, maxLength = 50)
        @NotBlank
        @Size(min = 2, max = 50)
        String lastName,
    @Schema(description = "Date of birth", example = "1995-06-15") @NotNull @Past
        LocalDate dateOfBirth,
    @Schema(description = "Phone number in E.164 format", example = "+905551234567") @Size(max = 20)
        String phoneNumber,
    @Schema(description = "Short biography", example = "Software developer from Istanbul")
        @Size(max = 500)
        String bio,
    @Schema(
            description = "Gender",
            example = "MALE",
            allowableValues = {"MALE", "FEMALE", "OTHER", "PREFER_NOT_TO_SAY"})
        Gender gender,
    @Schema(description = "City/country location", example = "İstanbul, Türkiye") @Size(max = 100)
        String location,
    @Schema(description = "MinIO object key from avatar upload", example = "avatars/uuid/img.jpg")
        String avatarKey) {}
