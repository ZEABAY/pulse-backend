package com.zeabay.pulse.mail.domain;

import org.springframework.data.r2dbc.repository.Modifying;
import org.springframework.data.r2dbc.repository.Query;
import org.springframework.data.r2dbc.repository.R2dbcRepository;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Mono;

@Repository
public interface InboxEventRepository extends R2dbcRepository<InboxEvent, String> {

  String CONSUMER_NAME = "mail-service";

  @Modifying
  @Query(
      "INSERT INTO inbox_events (event_id, consumer_name, trace_id) VALUES (:eventId, :consumerName, :traceId) "
          + "ON CONFLICT (event_id, consumer_name) DO NOTHING")
  Mono<Integer> insertIfAbsent(
      @Param("eventId") String eventId,
      @Param("consumerName") String consumerName,
      @Param("traceId") String traceId);

  default Mono<Boolean> tryInsert(String eventId, String traceId) {
    return insertIfAbsent(eventId, CONSUMER_NAME, traceId).map(rows -> rows > 0);
  }
}
