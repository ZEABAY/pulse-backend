package com.zeabay.pulse.mail.domain;

import java.time.Instant;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

@Table("inbox_events")
@Getter
@NoArgsConstructor
@AllArgsConstructor
public class InboxEvent {

  @Id
  @Column("event_id")
  private String eventId;

  @Column("consumer_name")
  private String consumerName;

  @Column("trace_id")
  private String traceId;

  @Column("processed_at")
  private Instant processedAt;
}
