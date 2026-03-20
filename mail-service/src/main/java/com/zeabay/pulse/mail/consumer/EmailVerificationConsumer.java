package com.zeabay.pulse.mail.consumer;

import com.zeabay.common.inbox.BaseConsumer;
import com.zeabay.pulse.shared.events.auth.EmailVerificationRequestedEvent;
import com.zeabay.pulse.shared.kafka.PulseTopics;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

/**
 * Kafka consumer that sends email verification messages.
 *
 * <p>Extends {@link BaseConsumer} to guarantee exactly-once delivery via the Inbox pattern.
 * Duplicate events (same {@code eventId} + {@code serviceName}) are automatically discarded by the
 * database unique constraint.
 */
@Slf4j
@Component
public class EmailVerificationConsumer extends BaseConsumer<EmailVerificationRequestedEvent> {

  private final JavaMailSender mailSender;

  @Value("${spring.mail.username}")
  private String mailFrom;

  public EmailVerificationConsumer(JavaMailSender mailSender) {
    this.mailSender = mailSender;
  }

  @KafkaListener(
      topics = PulseTopics.EMAIL_VERIFICATION,
      groupId = PulseTopics.CONSUMER_GROUP_ID_PATTERN,
      containerFactory = "zeabayKafkaListenerContainerFactory")
  public void consume(EmailVerificationRequestedEvent event) {
    super.handleEvent(event);
  }

  @Override
  protected Mono<Void> doProcess(EmailVerificationRequestedEvent event) {
    return Mono.fromRunnable(
            () -> sendVerificationEmail(event.getEmail(), event.getVerificationToken()))
        .doOnSuccess(
            _ ->
                log.info(
                    "Verification email sent: email={}, eventId={}",
                    event.getEmail(),
                    event.getEventId()))
        .onErrorMap(
            e -> {
              log.error(
                  "Failed to send verification email: eventId={}, error={}",
                  event.getEventId(),
                  e.getMessage());
              return new RuntimeException("Mail send failed", e);
            })
        .then();
  }

  private void sendVerificationEmail(String email, String token) {
    SimpleMailMessage message = new SimpleMailMessage();
    message.setTo(email);
    message.setSubject("Verify your Pulse account");
    message.setText("Your verification code: " + token);
    message.setFrom(mailFrom);
    mailSender.send(message);
    log.info("MAIL_SEND_SUCCESS: verification email delivered to {}", email);
  }
}
