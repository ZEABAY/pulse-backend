package com.zeabay.pulse.mail.infrastructure.consumer;

import com.zeabay.pulse.mail.application.service.PulseMailSender;
import com.zeabay.pulse.mail.domain.MailRequest;
import com.zeabay.pulse.mail.domain.MailType;
import com.zeabay.pulse.shared.events.auth.EmailVerificationRequestedEvent;
import com.zeabay.pulse.shared.kafka.PulseTopics;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/**
 * Kafka consumer that sends email verification messages.
 *
 * <p>Extends {@link BaseMailConsumer} which handles the actual sending via {@link PulseMailSender},
 * audit logging, and inbox idempotency. This class only maps the event to a {@link MailRequest}.
 */
@Component
public class EmailVerificationConsumer extends BaseMailConsumer<EmailVerificationRequestedEvent> {

  public EmailVerificationConsumer(PulseMailSender mailSender) {
    super(mailSender);
  }

  @KafkaListener(
      topics = PulseTopics.EMAIL_VERIFICATION,
      groupId = PulseTopics.CONSUMER_GROUP_ID_PATTERN,
      containerFactory = "zeabayKafkaListenerContainerFactory")
  public void consume(EmailVerificationRequestedEvent event) {
    super.handleEvent(event);
  }

  @Override
  protected MailRequest buildMailRequest(EmailVerificationRequestedEvent event) {
    return new MailRequest(
        event.getEventId(),
        event.getTraceId(),
        event.getUserId(),
        event.getEmail(),
        MailType.EMAIL_VERIFICATION,
        "Verify your Pulse account",
        "Your verification code: " + event.getVerificationToken());
  }
}
