package com.zeabay.pulse.mail.domain;

import org.springframework.data.repository.reactive.ReactiveCrudRepository;

/** Reactive repository for {@link SentMailLog} audit records. */
public interface SentMailLogRepository extends ReactiveCrudRepository<SentMailLog, Long> {}
