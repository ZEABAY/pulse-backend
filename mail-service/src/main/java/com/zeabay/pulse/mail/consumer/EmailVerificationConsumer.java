package com.zeabay.pulse.mail.consumer;

import com.zeabay.common.kafka.KafkaTopic;
import com.zeabay.common.kafka.event.auth.EmailVerificationRequestedEvent;
import com.zeabay.pulse.mail.domain.InboxEventRepository;
import java.time.Duration;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class EmailVerificationConsumer {

  @Value("${MAIL_FROM:${MAIL_USERNAME:noreply@pulse.local}}")
  private String mailFrom;

  private final InboxEventRepository inboxEventRepository;
  private final JavaMailSender mailSender;

  @KafkaListener(
      topics = KafkaTopic.EMAIL_VERIFICATION,
      groupId = "pulse-mail-service",
      containerFactory = "zeabayKafkaListenerContainerFactory")
  public void consume(EmailVerificationRequestedEvent event) {
    if (!Boolean.TRUE.equals(
        inboxEventRepository
            .tryInsert(event.getEventId(), event.getTraceId())
            .block(Duration.ofSeconds(5)))) {
      log.debug("Duplicate event ignored: eventId={}", event.getEventId());
      return;
    }

    try {
      sendVerificationEmail(event.getEmail(), event.getVerificationToken());
      log.info(
          "Verification email sent: email={}, eventId={}", event.getEmail(), event.getEventId());
    } catch (Exception e) {
      log.error(
          "Failed to send verification email: eventId={}, error={}",
          event.getEventId(),
          e.getMessage());
      throw new RuntimeException("Mail send failed", e);
    }
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
