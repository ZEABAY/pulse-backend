package com.zeabay.pulse.auth.infrastructure.adapter;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zeabay.common.outbox.OutboxEvent;
import com.zeabay.common.outbox.OutboxEventRepository;
import com.zeabay.pulse.auth.application.port.out.OutboxPort;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

/**
 * Infrastructure adapter that serializes domain events to JSON and persists them to the
 * transactional outbox table.
 *
 * <p>Implements {@link OutboxPort} so the application layer remains free of Jackson and R2DBC
 * repository dependencies.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OutboxAdapter implements OutboxPort {

  private final OutboxEventRepository repository;
  private final ObjectMapper objectMapper;

  @Override
  public Mono<Void> saveEvent(
      String eventType,
      String topic,
      String aggregateType,
      Long aggregateId,
      Object payload,
      String traceId) {
    return serializePayload(payload, eventType)
        .map(json -> buildOutboxEvent(eventType, topic, aggregateType, aggregateId, json, traceId))
        .flatMap(repository::save)
        .then();
  }

  /**
   * Serializes payload to JSON. Returns {@code Mono.error} on failure — critical for transaction
   * rollback when used inside {@code @Transactional} (e.g. registerUser).
   */
  private Mono<String> serializePayload(Object payload, String eventType) {
    try {
      return Mono.just(objectMapper.writeValueAsString(payload));
    } catch (JsonProcessingException e) {
      log.error("Failed to serialize outbox event [eventType={}]: {}", eventType, e.getMessage());
      return Mono.error(new RuntimeException("Failed to serialize outbox event: " + eventType, e));
    }
  }

  private OutboxEvent buildOutboxEvent(
      String eventType,
      String topic,
      String aggregateType,
      Long aggregateId,
      String jsonPayload,
      String traceId) {
    return OutboxEvent.builder()
        .eventType(eventType)
        .topic(topic)
        .aggregateType(aggregateType)
        .aggregateId(aggregateId)
        .payload(jsonPayload)
        .traceId(traceId)
        .status(OutboxEvent.Status.PENDING)
        .retryCount(0)
        .build();
  }
}
