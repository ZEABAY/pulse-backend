package com.zeabay.pulse.auth.domain.model;

import com.zeabay.common.r2dbc.BaseEntity;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
@Table("auth_users")
public class AuthUser extends BaseEntity {

  @Column("keycloak_id")
  private String keycloakId;

  @Column("email")
  private String email;

  @Column("username")
  private String username;

  @Builder.Default
  @Column("status")
  private AuthUserStatus status = AuthUserStatus.PENDING_VERIFICATION;
}
