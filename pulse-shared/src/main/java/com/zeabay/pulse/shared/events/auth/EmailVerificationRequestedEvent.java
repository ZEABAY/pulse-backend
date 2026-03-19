package com.zeabay.pulse.shared.events.auth;

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
 * Kafka event fired when a user requests email verification.
 *
 * <p>Published by {@code auth-service} and consumed by {@code mail-service}. Defined in {@code
 * pulse-shared} so that both services share the same class without coupling to each other.
 */
@Getter
@EqualsAndHashCode(callSuper = true, onlyExplicitlyIncluded = true)
@ToString(callSuper = true)
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonDeserialize(
    builder = EmailVerificationRequestedEvent.EmailVerificationRequestedEventBuilder.class)
public class EmailVerificationRequestedEvent extends BaseEvent {

  /** Canonical event type identifier used by consumers for routing and deserialization. */
  public static final String EVENT_TYPE = "EmailVerificationRequested";

  @EqualsAndHashCode.Include private final String userId;
  private final String email;
  private final String verificationToken;

  @Builder
  public EmailVerificationRequestedEvent(
      @JsonProperty("eventId") @JsonAlias("event_id") String eventId,
      @JsonProperty("traceId") @JsonAlias("trace_id") String traceId,
      @JsonProperty("occurredAt") @JsonAlias("occurred_at") Instant occurredAt,
      @JsonProperty("userId") @JsonAlias("user_id") String userId,
      @JsonProperty("email") String email,
      @JsonProperty("verificationToken") @JsonAlias("verification_token")
          String verificationToken) {
    super(eventId, traceId, occurredAt);
    this.userId = userId;
    this.email = email;
    this.verificationToken = verificationToken;
  }

  @Override
  public String getEventType() {
    return EVENT_TYPE;
  }
}
