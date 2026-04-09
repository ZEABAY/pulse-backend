package com.zeabay.pulse.auth.domain.repository;

import com.zeabay.pulse.auth.domain.model.AuthPasswordResetToken;
import java.time.Instant;
import org.springframework.data.r2dbc.repository.Modifying;
import org.springframework.data.r2dbc.repository.Query;
import org.springframework.data.r2dbc.repository.R2dbcRepository;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Mono;

/** Reactive repository for {@link AuthPasswordResetToken} entities. */
@Repository
public interface AuthPasswordResetTokenRepository
    extends R2dbcRepository<AuthPasswordResetToken, Long> {

  Mono<AuthPasswordResetToken> findByUserIdAndToken(Long userId, String token);

  @Modifying
  @Query(
      "UPDATE auth_password_reset_tokens SET used_at = :now WHERE user_id = :userId AND used_at IS NULL")
  Mono<Integer> invalidatePreviousTokensForUser(
      @Param("userId") Long userId, @Param("now") Instant now);
}
