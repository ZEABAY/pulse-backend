package com.zeabay.pulse.auth.domain.repository;

import com.zeabay.pulse.auth.domain.model.AuthVerificationToken;
import org.springframework.data.r2dbc.repository.R2dbcRepository;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Mono;

@Repository
public interface AuthVerificationTokenRepository
    extends R2dbcRepository<AuthVerificationToken, Long> {

  Mono<AuthVerificationToken> findByToken(String token);
}
