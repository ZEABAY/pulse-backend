package com.zeabay.pulse.auth.domain.model;

import com.zeabay.common.r2dbc.BaseEntity;
import java.lang.reflect.Field;

/**
 * Test-only factory for creating {@link AuthUser} with a specific id (inherited from BaseEntity).
 */
public final class AuthUserTestFixtures {

  private AuthUserTestFixtures() {}

  public static AuthUser withId(
      long id, String keycloakId, String username, String email, AuthUserStatus status) {
    var user =
        AuthUser.builder()
            .keycloakId(keycloakId)
            .username(username)
            .email(email)
            .status(status)
            .build();
    setId(user, id);
    return user;
  }

  private static void setId(AuthUser user, long id) {
    try {
      Field idField = BaseEntity.class.getDeclaredField("id");
      idField.setAccessible(true);
      idField.set(user, id);
    } catch (Exception e) {
      throw new RuntimeException("Failed to set id on AuthUser", e);
    }
  }
}
