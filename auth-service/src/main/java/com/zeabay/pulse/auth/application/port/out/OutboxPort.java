package com.zeabay.pulse.auth.application.port.out;

import reactor.core.publisher.Mono;

/**
 * Output port for persisting domain events to the transactional outbox.
 *
 * <p>Shields the application layer from infrastructure concerns: callers pass the raw payload
 * object and a topic; serialization and repository access happen in the adapter.
 */
public interface OutboxPort {

  /**
   * Serializes {@code payload} to JSON and saves a PENDING outbox event.
   *
   * @param eventType human-readable event type (e.g. "UserRegisteredEvent")
   * @param topic Kafka topic name (use {@link com.zeabay.common.kafka.KafkaTopic} constants)
   * @param aggregateType name of the aggregate root (e.g. "AuthUser")
   * @param aggregateId numeric TSID of the aggregate
   * @param payload the event object to serialize
   * @param traceId propagated trace identifier
   */
  Mono<Void> saveEvent(
      String eventType,
      String topic,
      String aggregateType,
      Long aggregateId,
      Object payload,
      String traceId);
}
