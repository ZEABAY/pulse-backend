package com.zeabay.pulse.auth.domain.model;

/** Lifecycle status of an {@link AuthUser}. */
public enum AuthUserStatus {
  PENDING_VERIFICATION,
  ACTIVE,
  SUSPENDED,
  DELETED
}
