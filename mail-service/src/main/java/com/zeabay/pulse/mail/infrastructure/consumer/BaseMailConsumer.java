package com.zeabay.pulse.mail.infrastructure.consumer;

import com.zeabay.common.inbox.BaseConsumer;
import com.zeabay.common.kafka.BaseEvent;
import com.zeabay.common.logging.Loggable;
import com.zeabay.pulse.mail.application.service.PulseMailSender;
import com.zeabay.pulse.mail.domain.MailRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Mono;

/**
 * Template-method base class for all mail-sending Kafka consumers.
 *
 * <p>Sits between {@link BaseConsumer} (inbox idempotency) and the concrete consumer
 * implementations. Concrete classes only need to:
 *
 * <ol>
 *   <li>Declare a {@code @KafkaListener} method that calls {@code super.handleEvent(event)}
 *   <li>Override {@link #buildMailRequest(BaseEvent)} to map the event to a {@link MailRequest}
 * </ol>
 *
 * <p>This class handles the actual sending via {@link PulseMailSender} and logging. The {@code
 * doProcess} method is {@code final} to enforce the template pattern.
 *
 * @param <T> the concrete domain event type
 */
@Slf4j
@Loggable
@RequiredArgsConstructor
public abstract class BaseMailConsumer<T extends BaseEvent> extends BaseConsumer<T> {

  private final PulseMailSender mailSender;

  /**
   * Maps the incoming domain event to a {@link MailRequest}.
   *
   * @param event the deduplicated domain event
   * @return a fully populated mail request
   */
  protected abstract MailRequest buildMailRequest(T event);

  /**
   * Sends the mail built from the event. This method is {@code final} — concrete consumers must not
   * override it; they customize behavior via {@link #buildMailRequest(BaseEvent)}.
   */
  @Override
  protected final Mono<Void> doProcess(T event) {
    MailRequest request = buildMailRequest(event);
    return mailSender
        .send(request)
        .doOnSuccess(
            _ ->
                log.info(
                    "MAIL_PROCESSED: type={}, to={}, eventId={}",
                    request.mailType(),
                    request.recipient(),
                    event.getEventId()))
        .then();
  }
}
