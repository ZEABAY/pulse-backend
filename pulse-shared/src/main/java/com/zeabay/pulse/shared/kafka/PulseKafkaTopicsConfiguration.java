package com.zeabay.pulse.shared.kafka;

import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;

/**
 * Centralizes Kafka topic creation for all Pulse services.
 *
 * <p>Spring Kafka's {@link org.springframework.kafka.core.KafkaAdmin} automatically discovers all
 * {@link NewTopic} beans on the classpath and creates (or verifies) the topics at startup. By
 * placing the definitions here, each service that imports {@code pulse-shared} participates in
 * topic creation without duplicating partition/replication configuration in its own YAML.
 */
@Slf4j
@Configuration
public class PulseKafkaTopicsConfiguration {

  /** Primary topic for {@code EmailVerificationRequestedEvent}. */
  @Bean
  public NewTopic emailVerificationTopic() {
    return declare(PulseTopics.EMAIL_VERIFICATION);
  }

  /** Dead-letter queue for failed email verification messages. */
  @Bean
  public NewTopic emailVerificationDlqTopic() {
    return declare(PulseTopics.EMAIL_VERIFICATION_DLQ);
  }

  /** Topic for {@code PasswordResetRequestedEvent}. */
  @Bean
  public NewTopic passwordResetTopic() {
    return declare(PulseTopics.PASSWORD_RESET);
  }

  /** Dead-letter queue for failed password reset messages. */
  @Bean
  public NewTopic passwordResetDlqTopic() {
    return declare(PulseTopics.PASSWORD_RESET_DLQ);
  }

  // ─── User verification (profile creation trigger) ─────────────────────────

  /** Topic for {@code UserVerifiedEvent}. */
  @Bean
  public NewTopic userVerifiedTopic() {
    return declare(PulseTopics.USER_VERIFIED);
  }

  /** Dead-letter queue for failed user verified messages. */
  @Bean
  public NewTopic userVerifiedDlqTopic() {
    return declare(PulseTopics.USER_VERIFIED_DLQ);
  }

  // ─── Mail delivery failure (saga compensation) ─────────────────────────────

  /** Topic for {@code EmailVerificationMailFailedEvent} (saga compensation). */
  @Bean
  public NewTopic emailVerificationMailFailedTopic() {
    return declare(PulseTopics.EMAIL_VERIFICATION_MAIL_FAILED);
  }

  /** Dead-letter queue for {@link #emailVerificationMailFailedTopic}. */
  @Bean
  public NewTopic emailVerificationMailFailedDlqTopic() {
    return declare(PulseTopics.EMAIL_VERIFICATION_MAIL_FAILED_DLQ);
  }

  /** Topic for {@code PasswordResetMailFailedEvent}. */
  @Bean
  public NewTopic passwordResetMailFailedTopic() {
    return declare(PulseTopics.PASSWORD_RESET_MAIL_FAILED);
  }

  /** Dead-letter queue for {@link #passwordResetMailFailedTopic}. */
  @Bean
  public NewTopic passwordResetMailFailedDlqTopic() {
    return declare(PulseTopics.PASSWORD_RESET_MAIL_FAILED_DLQ);
  }

  private static NewTopic declare(String name) {
    return declare(name, 3, 1);
  }

  @SuppressWarnings("SameParameterValue")
  private static NewTopic declare(String name, int partitions, int replicas) {
    log.info(
        "[Kafka] Declaring topic '{}' (partitions={}, replicas={})", name, partitions, replicas);
    return TopicBuilder.name(name).partitions(partitions).replicas(replicas).build();
  }
}
