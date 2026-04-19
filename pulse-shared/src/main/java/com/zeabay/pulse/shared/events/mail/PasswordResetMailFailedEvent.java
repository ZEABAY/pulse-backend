package com.zeabay.pulse.shared.events.mail;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.zeabay.common.kafka.BaseEvent;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.ToString;
import lombok.experimental.SuperBuilder;
import lombok.extern.jackson.Jacksonized;

/**
 * Kafka event fired when a password reset mail delivery fails.
 *
 * <p>Published by {@code mail-service}. Currently not consumed (password reset failures don't
 * require compensation — users can simply retry). Published for audit and future extensibility.
 */
@Getter
@SuperBuilder
@Jacksonized
@EqualsAndHashCode(callSuper = true, onlyExplicitlyIncluded = true)
@ToString(callSuper = true)
@JsonIgnoreProperties(ignoreUnknown = true)
public class PasswordResetMailFailedEvent extends BaseEvent {

  public static final String EVENT_TYPE = "PasswordResetMailFailed";

  @EqualsAndHashCode.Include private final String userId;
  private final String email;
  private final String errorMessage;

  @Override
  public String getEventType() {
    return EVENT_TYPE;
  }
}
