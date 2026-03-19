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
    return declare(PulseTopics.EMAIL_VERIFICATION, 3, 1);
  }

  /** Dead-letter queue for failed email verification messages. */
  @Bean
  public NewTopic emailVerificationDlqTopic() {
    return declare(PulseTopics.EMAIL_VERIFICATION_DLQ, 3, 1);
  }

  private static NewTopic declare(String name, int partitions, int replicas) {
    log.info(
        "[Kafka] Declaring topic '{}' (partitions={}, replicas={})", name, partitions, replicas);
    return TopicBuilder.name(name).partitions(partitions).replicas(replicas).build();
  }
}
