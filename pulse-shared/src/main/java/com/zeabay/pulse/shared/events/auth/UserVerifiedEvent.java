package com.zeabay.pulse.shared.events.auth;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.zeabay.common.kafka.BaseEvent;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.ToString;
import lombok.experimental.SuperBuilder;
import lombok.extern.jackson.Jacksonized;

/**
 * Kafka event fired when a user's email is verified successfully.
 *
 * <p>Published by {@code auth-service} and consumed by {@code profile-service} to create a skeleton
 * user profile. Defined in {@code pulse-shared} so that both services share the same class without
 * coupling to each other.
 */
@Getter
@SuperBuilder
@Jacksonized
@EqualsAndHashCode(callSuper = true, onlyExplicitlyIncluded = true)
@ToString(callSuper = true)
@JsonIgnoreProperties(ignoreUnknown = true)
public class UserVerifiedEvent extends BaseEvent {

  /** Canonical event type identifier used by consumers for routing and deserialization. */
  public static final String EVENT_TYPE = "UserVerified";

  @EqualsAndHashCode.Include private final String userId;
  private final String keycloakId;
  private final String username;
  private final String email;

  @Override
  public String getEventType() {
    return EVENT_TYPE;
  }
}
