package com.zeabay.pulse.profile.domain.model;

import com.zeabay.common.r2dbc.BaseEntity;
import java.time.LocalDate;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

/**
 * R2DBC entity representing a user profile in the profile-service schema.
 *
 * <p>The {@code keycloakId} and {@code username} fields are populated via {@code UserVerifiedEvent}
 * from auth-service. All other fields are set when the user completes their profile.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
@Table("user_profiles")
public class UserProfile extends BaseEntity {

  @Column("keycloak_id")
  private String keycloakId;

  @Column("username")
  private String username;

  @Column("first_name")
  private String firstName;

  @Column("last_name")
  private String lastName;

  @Column("date_of_birth")
  private LocalDate dateOfBirth;

  @Column("phone_number")
  private String phoneNumber;

  @Column("avatar_url")
  private String avatarUrl;

  @Column("avatar_key")
  private String avatarKey;

  @Column("bio")
  private String bio;

  @Column("gender")
  private Gender gender;

  @Column("location")
  private String location;

  @Builder.Default
  @Column("profile_completed")
  private boolean profileCompleted = false;
}
