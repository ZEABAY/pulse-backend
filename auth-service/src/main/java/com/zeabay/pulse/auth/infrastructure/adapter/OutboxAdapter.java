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

    String json;
    try {
      json = objectMapper.writeValueAsString(payload);
    } catch (JsonProcessingException e) {
      log.error("Failed to serialize outbox event [eventType={}]: {}", eventType, e.getMessage());
      return Mono.error(new RuntimeException("Failed to serialize outbox event: " + eventType, e));
    }

    OutboxEvent event =
        OutboxEvent.builder()
            .eventType(eventType)
            .topic(topic)
            .aggregateType(aggregateType)
            .aggregateId(aggregateId)
            .payload(json)
            .traceId(traceId)
            .status(OutboxEvent.Status.PENDING)
            .retryCount(0)
            .build();

    return repository.save(event).then();
  }
}
