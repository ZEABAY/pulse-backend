package com.zeabay.pulse.mail.application.service;

import com.zeabay.common.logging.Loggable;
import com.zeabay.pulse.mail.application.port.out.OutboxPort;
import com.zeabay.pulse.mail.domain.MailRequest;
import com.zeabay.pulse.mail.domain.MailStatus;
import com.zeabay.pulse.mail.domain.SentMailLog;
import com.zeabay.pulse.mail.domain.SentMailLogRepository;
import com.zeabay.pulse.shared.events.mail.EmailVerificationMailFailedEvent;
import com.zeabay.pulse.shared.events.mail.PasswordResetMailFailedEvent;
import com.zeabay.pulse.shared.kafka.PulseTopics;
import java.time.Instant;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

/**
 * Central mail delivery service. All mail sending must go through this class — consumers never
 * interact with {@link JavaMailSender} directly.
 *
 * <p>Responsibilities:
 *
 * <ul>
 *   <li>Send the actual email via {@link JavaMailSender}
 *   <li>Persist a {@link SentMailLog} audit record (success or failure)
 *   <li>On failure: persist a per-type fail event to the outbox table for saga compensation
 * </ul>
 */
@Loggable
@Slf4j
@Service
@RequiredArgsConstructor
public class PulseMailSender {

  private final JavaMailSender javaMailSender;
  private final SentMailLogRepository logRepository;
  private final OutboxPort outboxPort;

  @Value("${spring.mail.username}")
  private String mailFrom;

  /**
   * Sends a mail, persists an audit log entry, and publishes a failure event via outbox on error.
   *
   * <p>On success: saves a {@code SENT} log and returns {@link MailStatus#SENT}. On failure: saves
   * a {@code FAILED} log, persists a per-type failure event to the outbox table for saga
   * compensation, and <b>re-throws</b> the error.
   *
   * @param request the mail delivery request
   * @return a {@link Mono} that emits the delivery status
   */
  public Mono<MailStatus> send(MailRequest request) {
    return Mono.fromRunnable(() -> doSend(request))
        .then(saveLog(request, MailStatus.SENT, null))
        .thenReturn(MailStatus.SENT)
        .onErrorResume(
            e -> {
              log.error(
                  "MAIL_SEND_FAILED: type={}, to={}, eventId={}, error={}",
                  request.mailType(),
                  request.recipient(),
                  request.eventId(),
                  e.getMessage());
              return saveLog(request, MailStatus.FAILED, e.getMessage())
                  .then(publishFailedEvent(request, e.getMessage()))
                  .then(Mono.error(e));
            });
  }

  private void doSend(MailRequest request) {
    SimpleMailMessage message = new SimpleMailMessage();
    message.setTo(request.recipient());
    message.setSubject(request.subject());
    message.setText(request.body());
    message.setFrom(mailFrom);
    javaMailSender.send(message);
  }

  private Mono<Void> saveLog(MailRequest request, MailStatus status, String errorMessage) {
    SentMailLog entry =
        SentMailLog.builder()
            .eventId(request.eventId())
            .traceId(request.traceId())
            .recipient(request.recipient())
            .mailType(request.mailType().name())
            .subject(request.subject())
            .status(status.name())
            .errorMessage(errorMessage)
            .sentAt(Instant.now())
            .build();

    return logRepository
        .save(entry)
        .doOnError(
            e ->
                log.warn(
                    "MAIL_LOG_SAVE_FAILED: eventId={}, error={}",
                    request.eventId(),
                    e.getMessage()))
        .then();
  }

  /**
   * Publishes the appropriate per-type failure event to the outbox table. The {@link
   * com.zeabay.common.outbox.OutboxPublisher} will poll and deliver it to Kafka.
   */
  private Mono<Void> publishFailedEvent(MailRequest request, String errorMessage) {
    return switch (request.mailType()) {
      case EMAIL_VERIFICATION -> publishEmailVerificationFailed(request, errorMessage);
      case PASSWORD_RESET -> publishPasswordResetFailed(request, errorMessage);
    };
  }

  private Mono<Void> publishEmailVerificationFailed(MailRequest request, String errorMessage) {
    var event =
        EmailVerificationMailFailedEvent.builder()
            .eventId(UUID.randomUUID().toString())
            .traceId(request.traceId())
            .occurredAt(Instant.now())
            .userId(request.userId())
            .email(request.recipient())
            .errorMessage(errorMessage)
            .build();

    return outboxPort.saveEvent(
        event.getEventId(),
        EmailVerificationMailFailedEvent.EVENT_TYPE,
        PulseTopics.EMAIL_VERIFICATION_MAIL_FAILED,
        "SentMailLog",
        0L,
        event,
        request.traceId());
  }

  private Mono<Void> publishPasswordResetFailed(MailRequest request, String errorMessage) {
    var event =
        PasswordResetMailFailedEvent.builder()
            .eventId(UUID.randomUUID().toString())
            .traceId(request.traceId())
            .occurredAt(Instant.now())
            .userId(request.userId())
            .email(request.recipient())
            .errorMessage(errorMessage)
            .build();

    return outboxPort.saveEvent(
        event.getEventId(),
        PasswordResetMailFailedEvent.EVENT_TYPE,
        PulseTopics.PASSWORD_RESET_MAIL_FAILED,
        "SentMailLog",
        0L,
        event,
        request.traceId());
  }
}
