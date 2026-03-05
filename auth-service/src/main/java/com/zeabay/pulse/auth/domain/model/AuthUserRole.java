package com.zeabay.pulse.auth.domain.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table("auth_user_roles")
public class AuthUserRole {

  @Id private Long id;

  @Column("auth_user_id")
  private Long authUserId;

  @Column("role_id")
  private Long roleId;
}
