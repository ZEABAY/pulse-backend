package com.zeabay.pulse.mail.domain;

import java.time.Instant;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

/**
 * Lightweight audit record for every mail delivery attempt.
 *
 * <p>Does <b>not</b> extend {@code BaseEntity} intentionally — a mail log has no need for
 * soft-delete or created_by/updated_by audit columns. The {@code @Id Long id} field is still
 * auto-populated with a TSID by {@code zeabayGenericTsidBeforeConvertCallback}.
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table("sent_mail_log")
public class SentMailLog {

  @Id private Long id;

  @Column("event_id")
  private String eventId;

  @Column("trace_id")
  private String traceId;

  @Column("recipient")
  private String recipient;

  @Column("mail_type")
  private String mailType;

  @Column("subject")
  private String subject;

  @Column("status")
  private String status;

  @Column("error_message")
  private String errorMessage;

  @Column("sent_at")
  private Instant sentAt;
}
