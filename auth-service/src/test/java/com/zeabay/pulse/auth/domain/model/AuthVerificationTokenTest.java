package com.zeabay.pulse.auth.domain.model;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import org.junit.jupiter.api.Test;

class AuthVerificationTokenTest {

  @Test
  void isExpired_whenExpiresAtInPast_returnsTrue() {
    var token = AuthVerificationToken.builder().expiresAt(Instant.now().minusSeconds(1)).build();
    assertThat(token.isExpired()).isTrue();
  }

  @Test
  void isExpired_whenExpiresAtInFuture_returnsFalse() {
    var token = AuthVerificationToken.builder().expiresAt(Instant.now().plusSeconds(3600)).build();
    assertThat(token.isExpired()).isFalse();
  }

  @Test
  void isUsed_whenUsedAtIsSet_returnsTrue() {
    var token =
        AuthVerificationToken.builder()
            .expiresAt(Instant.now().plusSeconds(3600))
            .usedAt(Instant.now())
            .build();
    assertThat(token.isUsed()).isTrue();
  }

  @Test
  void isUsed_whenUsedAtIsNull_returnsFalse() {
    var token = AuthVerificationToken.builder().expiresAt(Instant.now().plusSeconds(3600)).build();
    assertThat(token.isUsed()).isFalse();
  }
}
