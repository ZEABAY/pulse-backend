package com.zeabay.pulse.mail.consumer;

import com.zeabay.common.inbox.BaseConsumer;
import com.zeabay.pulse.shared.events.auth.PasswordResetRequestedEvent;
import com.zeabay.pulse.shared.kafka.PulseTopics;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

/**
 * Kafka consumer that sends password reset email.
 *
 * <p>Extends {@link BaseConsumer} to guarantee exactly-once delivery via the Inbox pattern.
 * Duplicate events (same {@code eventId} + {@code serviceName}) are automatically discarded by the
 * database unique constraint.
 */
@Slf4j
@Component
public class PasswordResetConsumer extends BaseConsumer<PasswordResetRequestedEvent> {

  private final JavaMailSender mailSender;

  @Value("${spring.mail.username}")
  private String mailFrom;

  public PasswordResetConsumer(JavaMailSender mailSender) {
    this.mailSender = mailSender;
  }

  @KafkaListener(
      topics = PulseTopics.PASSWORD_RESET,
      groupId = PulseTopics.CONSUMER_GROUP_ID_PATTERN,
      containerFactory = "zeabayKafkaListenerContainerFactory")
  public void consume(PasswordResetRequestedEvent event) {
    super.handleEvent(event);
  }

  @Override
  protected Mono<Void> doProcess(PasswordResetRequestedEvent event) {
    return Mono.fromRunnable(() -> sendPasswordResetEmail(event.getEmail(), event.getResetToken()))
        .doOnSuccess(
            _ ->
                log.info(
                    "PASSWORD_RESET_MAIL_PROCESSED: email={}, eventId={}",
                    event.getEmail(),
                    event.getEventId()))
        .onErrorMap(
            e -> {
              log.error(
                  "PASSWORD_RESET_MAIL_FAILED: email={}, eventId={}, error={}",
                  event.getEmail(),
                  event.getEventId(),
                  e.getMessage());
              return new RuntimeException("Mail send failed", e);
            })
        .then();
  }

  private void sendPasswordResetEmail(String email, String token) {
    SimpleMailMessage message = new SimpleMailMessage();
    message.setTo(email);
    message.setSubject("Reset your Pulse password");
    message.setText("Your password reset code: " + token);
    message.setFrom(mailFrom);
    mailSender.send(message);
    log.info("PASSWORD_RESET_MAIL_DELIVERED: to {}", email);
  }
}
