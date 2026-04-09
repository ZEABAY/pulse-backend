package com.zeabay.pulse.shared.events.mail;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.zeabay.common.kafka.BaseEvent;
import java.time.Instant;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.ToString;

/**
 * Kafka event fired when an email verification mail delivery fails.
 *
 * <p>Published by {@code mail-service} and consumed by {@code auth-service} to roll back the
 * pending registration (delete user from Keycloak + auth_users).
 */
@Getter
@EqualsAndHashCode(callSuper = true, onlyExplicitlyIncluded = true)
@ToString(callSuper = true)
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonDeserialize(
    builder = EmailVerificationMailFailedEvent.EmailVerificationMailFailedEventBuilder.class)
public class EmailVerificationMailFailedEvent extends BaseEvent {

  public static final String EVENT_TYPE = "EmailVerificationMailFailed";

  @EqualsAndHashCode.Include private final String userId;
  private final String email;
  private final String errorMessage;

  @Builder
  public EmailVerificationMailFailedEvent(
      @JsonProperty("eventId") @JsonAlias("event_id") String eventId,
      @JsonProperty("traceId") @JsonAlias("trace_id") String traceId,
      @JsonProperty("occurredAt") @JsonAlias("occurred_at") Instant occurredAt,
      @JsonProperty("userId") @JsonAlias("user_id") String userId,
      @JsonProperty("email") String email,
      @JsonProperty("errorMessage") @JsonAlias("error_message") String errorMessage) {
    super(eventId, traceId, occurredAt);
    this.userId = userId;
    this.email = email;
    this.errorMessage = errorMessage;
  }

  @Override
  public String getEventType() {
    return EVENT_TYPE;
  }
}
