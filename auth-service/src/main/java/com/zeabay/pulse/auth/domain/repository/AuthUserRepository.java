package com.zeabay.pulse.auth.domain.repository;

import com.zeabay.pulse.auth.domain.model.AuthUser;
import org.springframework.data.r2dbc.repository.R2dbcRepository;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Mono;

/** Reactive repository for {@link AuthUser} entities. */
@Repository
public interface AuthUserRepository extends R2dbcRepository<AuthUser, Long> {

  Mono<AuthUser> findByEmail(String email);

  Mono<AuthUser> findByUsername(String username);
}
