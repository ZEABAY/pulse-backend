package com.zeabay.pulse.auth.domain.model;

import java.time.Instant;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table("auth_verification_tokens")
public class AuthVerificationToken {

  @Id private Long id;

  @Column("user_id")
  private Long userId;

  @Column("token")
  private String token;

  @Column("expires_at")
  private Instant expiresAt;

  @Column("used_at")
  private Instant usedAt;

  @Column("created_at")
  private Instant createdAt;

  public boolean isExpired() {
    return Instant.now().isAfter(expiresAt);
  }

  public boolean isUsed() {
    return usedAt != null;
  }
}
