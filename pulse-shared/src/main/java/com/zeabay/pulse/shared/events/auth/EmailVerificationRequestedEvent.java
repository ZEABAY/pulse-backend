package com.zeabay.pulse.shared.events.auth;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.zeabay.common.kafka.BaseEvent;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.ToString;
import lombok.experimental.SuperBuilder;
import lombok.extern.jackson.Jacksonized;

/**
 * Kafka event fired when a user requests email verification.
 *
 * <p>Published by {@code auth-service} and consumed by {@code mail-service}. Defined in {@code
 * pulse-shared} so that both services share the same class without coupling to each other.
 */
@Getter
@SuperBuilder
@Jacksonized
@EqualsAndHashCode(callSuper = true, onlyExplicitlyIncluded = true)
@ToString(callSuper = true)
@JsonIgnoreProperties(ignoreUnknown = true)
public class EmailVerificationRequestedEvent extends BaseEvent {

  /** Canonical event type identifier used by consumers for routing and deserialization. */
  public static final String EVENT_TYPE = "EmailVerificationRequested";

  @EqualsAndHashCode.Include private final String userId;
  private final String email;
  private final String verificationToken;

  @Override
  public String getEventType() {
    return EVENT_TYPE;
  }
}
