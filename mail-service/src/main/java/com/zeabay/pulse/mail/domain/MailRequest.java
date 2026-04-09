package com.zeabay.pulse.mail.domain;

/**
 * Immutable value object that encapsulates everything needed to send a single mail.
 *
 * <p>Built by concrete consumer implementations via {@code BaseMailConsumer#buildMailRequest} and
 * consumed by {@link com.zeabay.pulse.mail.application.service.PulseMailSender}.
 *
 * @param eventId the originating outbox/domain event ID (for traceability)
 * @param traceId W3C trace identifier propagated from the producing service
 * @param userId the user ID associated with this mail (needed for saga compensation)
 * @param recipient the email address to send to
 * @param mailType category of this mail (EMAIL_VERIFICATION, PASSWORD_RESET)
 * @param subject email subject line
 * @param body plain-text email body
 */
public record MailRequest(
    String eventId,
    String traceId,
    String userId,
    String recipient,
    MailType mailType,
    String subject,
    String body) {}
