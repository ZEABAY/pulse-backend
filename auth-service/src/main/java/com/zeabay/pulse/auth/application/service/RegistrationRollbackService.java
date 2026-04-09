package com.zeabay.pulse.auth.application.service;

import com.zeabay.common.logging.Loggable;
import com.zeabay.pulse.auth.application.port.IdentityProviderPort;
import com.zeabay.pulse.auth.domain.model.AuthUserStatus;
import com.zeabay.pulse.auth.domain.repository.AuthUserRepository;
import com.zeabay.pulse.auth.domain.repository.AuthVerificationTokenRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import reactor.core.publisher.Mono;

/**
 * Compensating service for the registration saga.
 *
 * <p>When the mail-service fails to deliver a verification email, it publishes an {@code
 * EmailVerificationMailFailedEvent}. This service handles the rollback by deleting the user from
 * both Keycloak and the local database, allowing them to re-register with the same email/username.
 *
 * <p>All operations are idempotent — if the user has already been deleted or verified, the rollback
 * is a no-op.
 */
@Slf4j
@Service
@Loggable
@RequiredArgsConstructor
public class RegistrationRollbackService {

  private final AuthUserRepository userRepository;
  private final AuthVerificationTokenRepository verificationTokenRepository;
  private final IdentityProviderPort identityProvider;

  /**
   * Rolls back a pending registration by deleting the user from Keycloak and the local database.
   *
   * <p>Only users with {@link AuthUserStatus#PENDING_VERIFICATION} status are eligible for
   * rollback. If the user has already been verified (ACTIVE) or doesn't exist, the operation is
   * silently skipped.
   *
   * @param email the email address of the user whose registration should be rolled back
   * @return a {@link Mono} that completes when the rollback is done (or skipped)
   */
  @Transactional
  public Mono<Void> rollback(String email) {
    return userRepository
        .findByEmail(email)
        .filter(user -> user.getStatus() == AuthUserStatus.PENDING_VERIFICATION)
        .flatMap(
            user -> {
              log.info(
                  "REGISTRATION_ROLLBACK: Rolling back pending registration for email={}, keycloakId={}",
                  email,
                  user.getKeycloakId());

              return identityProvider
                  .deleteUser(user.getKeycloakId())
                  .onErrorResume(
                      e -> {
                        log.warn(
                            "KEYCLOAK_DELETE_SKIPPED: keycloakId={}, error={}",
                            user.getKeycloakId(),
                            e.getMessage());
                        return Mono.empty();
                      })
                  .then(verificationTokenRepository.deleteById(user.getId()))
                  .then(userRepository.delete(user))
                  .doOnSuccess(
                      _ ->
                          log.info(
                              "REGISTRATION_ROLLBACK_COMPLETE: email={}, userId={}",
                              email,
                              user.getId()));
            })
        .switchIfEmpty(
            Mono.fromRunnable(
                () ->
                    log.debug(
                        "REGISTRATION_ROLLBACK_SKIPPED: No pending user found for email={}",
                        email)));
  }
}
