package com.zeabay.pulse.auth.infrastructure.adapter;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.zeabay.common.outbox.BaseProducer;
import com.zeabay.common.outbox.OutboxEventRepository;
import com.zeabay.pulse.auth.application.port.out.OutboxPort;
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
public class OutboxAdapter extends BaseProducer implements OutboxPort {

  public OutboxAdapter(OutboxEventRepository repository, ObjectMapper objectMapper) {
    super(repository, objectMapper);
  }

  @Override
  public Mono<Void> saveEvent(
      String eventId,
      String eventType,
      String topic,
      String aggregateType,
      Long aggregateId,
      Object payload,
      String traceId) {
    return super.saveOutboxEvent(
        eventId, eventType, topic, aggregateType, aggregateId, payload, traceId);
  }
}
