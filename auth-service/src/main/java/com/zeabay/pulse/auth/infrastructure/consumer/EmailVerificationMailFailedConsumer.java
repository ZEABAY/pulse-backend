package com.zeabay.pulse.auth.infrastructure.consumer;

import com.zeabay.common.inbox.BaseConsumer;
import com.zeabay.pulse.auth.application.service.RegistrationRollbackService;
import com.zeabay.pulse.shared.events.mail.EmailVerificationMailFailedEvent;
import com.zeabay.pulse.shared.kafka.PulseTopics;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

/**
 * Kafka consumer that handles email verification mail delivery failures.
 *
 * <p>When the mail-service cannot deliver a verification email, it publishes an {@link
 * EmailVerificationMailFailedEvent}. This consumer triggers the registration rollback saga via
 * {@link RegistrationRollbackService}, deleting the pending user from Keycloak and the local
 * database so they can re-register with the same credentials.
 *
 * <p>Extends {@link BaseConsumer} for inbox idempotency — duplicate failure events are safely
 * discarded.
 */
@Slf4j
@Component
public class EmailVerificationMailFailedConsumer
    extends BaseConsumer<EmailVerificationMailFailedEvent> {

  private final RegistrationRollbackService rollbackService;

  public EmailVerificationMailFailedConsumer(RegistrationRollbackService rollbackService) {
    this.rollbackService = rollbackService;
  }

  @KafkaListener(
      topics = PulseTopics.EMAIL_VERIFICATION_MAIL_FAILED,
      groupId = PulseTopics.CONSUMER_GROUP_ID_PATTERN,
      containerFactory = "zeabayKafkaListenerContainerFactory")
  public void consume(EmailVerificationMailFailedEvent event) {
    super.handleEvent(event);
  }

  @Override
  protected Mono<Void> doProcess(EmailVerificationMailFailedEvent event) {
    log.info(
        "EMAIL_VERIFICATION_MAIL_FAILED_RECEIVED: userId={}, email={}, error={}",
        event.getUserId(),
        event.getEmail(),
        event.getErrorMessage());

    return rollbackService.rollback(event.getEmail());
  }
}
