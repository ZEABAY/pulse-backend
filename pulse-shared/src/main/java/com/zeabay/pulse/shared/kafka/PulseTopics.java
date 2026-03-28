package com.zeabay.pulse.shared.kafka;

/**
 * Canonical Kafka topic name constants for all Pulse services.
 *
 * <p>Topic names are built from {@link #PREFIX}, service name, and event name constants via string
 * concatenation, ensuring compile-time constant values usable in annotations.
 *
 * <p>Naming convention: {@code <prefix>.<service>.<event>} Example: {@code
 * pulse.auth.email-verification}
 */
public final class PulseTopics {

  /** Common prefix for all Pulse Kafka topics. Change here to rename the whole namespace. */
  static final String PREFIX = "pulse";

  // ─── Service names ──────────────────────────────────────────────────────────

  static final String AUTH = "auth";

  // add more service constants here as needed

  /**
   * Spring property placeholder pattern for Kafka consumer group IDs.
   *
   * <p>Resolves to {@code pulse-<spring.application.name>} at runtime (e.g. {@code
   * pulse-mail-service}). Use directly in {@code @KafkaListener(groupId = ...)}.
   */
  public static final String CONSUMER_GROUP_ID_PATTERN = PREFIX + "-${spring.application.name}";

  /** Topic on which auth-service publishes email verification requests. */
  public static final String EMAIL_VERIFICATION = PREFIX + "." + AUTH + ".email-verification";

  /** Dead-letter queue for {@link #EMAIL_VERIFICATION}. */
  public static final String EMAIL_VERIFICATION_DLQ = EMAIL_VERIFICATION + ".dlq";

  /** Topic on which auth-service publishes password reset requests. */
  public static final String PASSWORD_RESET = PREFIX + "." + AUTH + ".password-reset";

  /** Dead-letter queue for {@link #PASSWORD_RESET}. */
  public static final String PASSWORD_RESET_DLQ = PASSWORD_RESET + ".dlq";

  private PulseTopics() {}
}
