package com.zeabay.pulse.auth.application.service;

import com.zeabay.common.api.exception.BusinessException;
import com.zeabay.common.api.exception.ErrorCode;
import com.zeabay.common.constant.ZeabayConstants;
import com.zeabay.common.kafka.KafkaTopic;
import com.zeabay.common.kafka.event.auth.EmailVerificationRequestedEvent;
import com.zeabay.common.kafka.event.user.UserRegisteredEvent;
import com.zeabay.common.logging.Loggable;
import com.zeabay.common.security.OtpGenerator;
import com.zeabay.common.tsid.TsidIdGenerator;
import com.zeabay.common.validation.ZeabayValidator;
import com.zeabay.pulse.auth.application.dto.AuthTokenResult;
import com.zeabay.pulse.auth.application.dto.LoginCommand;
import com.zeabay.pulse.auth.application.dto.RegisterUserCommand;
import com.zeabay.pulse.auth.application.port.IdentityProviderPort;
import com.zeabay.pulse.auth.application.port.out.OutboxPort;
import com.zeabay.pulse.auth.application.usecase.AuthService;
import com.zeabay.pulse.auth.domain.model.AuthUser;
import com.zeabay.pulse.auth.domain.model.AuthUserStatus;
import com.zeabay.pulse.auth.domain.model.AuthVerificationToken;
import com.zeabay.pulse.auth.domain.repository.AuthUserRepository;
import com.zeabay.pulse.auth.domain.repository.AuthVerificationTokenRepository;
import java.time.Duration;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import reactor.core.publisher.Mono;
import reactor.util.function.Tuples;

@Slf4j
@Service
@Loggable
@RequiredArgsConstructor
public class AuthServiceImpl implements AuthService {

  private static final String DEFAULT_ROLE = "user";
  private static final String AGGREGATE_TYPE = "AuthUser";

  private final AuthUserRepository userRepository;
  private final AuthVerificationTokenRepository verificationTokenRepository;
  private final IdentityProviderPort identityProviderPort;
  private final OutboxPort outboxPort;
  private final OtpGenerator otpGenerator;
  private final TsidIdGenerator tsidIdGenerator;

  @Override
  @Transactional
  public Mono<AuthUser> registerUser(RegisterUserCommand command) {
    return validateOrError(command)
        .then(
            Mono.deferContextual(
                ctx -> {
                  String traceId = ctx.getOrDefault(ZeabayConstants.TRACE_ID_CTX_KEY, "");
                  return checkEmailNotExists(command.email())
                      .then(identityProviderPort.registerUser(command))
                      .flatMap(keycloakId -> createAndPersistUser(keycloakId, command, traceId))
                      .flatMap(
                          savedUser ->
                              identityProviderPort
                                  .assignRole(savedUser.getKeycloakId(), DEFAULT_ROLE)
                                  .thenReturn(savedUser))
                      .flatMap(
                          savedUser ->
                              createVerificationToken(savedUser)
                                  .flatMap(
                                      tuple ->
                                          publishRegistrationEvents(
                                                  tuple.getT1(), tuple.getT2(), traceId)
                                              .thenReturn(tuple.getT1())));
                }));
  }

  // ── Step 1 ────────────────────────────────────────────────────────────────

  /** Fails fast with {@link ErrorCode#USER_ALREADY_EXISTS} if the email is already taken. */
  private Mono<Void> checkEmailNotExists(String email) {
    return userRepository
        .findByEmail(email)
        .hasElement()
        .flatMap(
            exists -> {
              if (exists) {
                return Mono.error(
                    new BusinessException(
                        ErrorCode.USER_ALREADY_EXISTS, "Email is already registered"));
              }
              return Mono.empty();
            });
  }

  // ── Step 2 ────────────────────────────────────────────────────────────────

  /**
   * Saves the new user. If DB save fails, rolls back the Keycloak user as a compensating action.
   * {@link DataIntegrityViolationException} (race condition) is mapped to {@link
   * ErrorCode#USER_ALREADY_EXISTS}.
   */
  private Mono<AuthUser> createAndPersistUser(
      String keycloakId, RegisterUserCommand command, String traceId) {
    AuthUser newUser =
        AuthUser.builder()
            .keycloakId(keycloakId)
            .username(command.username())
            .email(command.email())
            .build();

    return userRepository
        .save(newUser)
        .onErrorResume(
            ex -> {
              log.error(
                  "[traceId={}] DB save failed for user {}; rolling back Keycloak user {}: {}",
                  traceId,
                  command.email(),
                  keycloakId,
                  ex.getMessage());
              return identityProviderPort.deleteUser(keycloakId).then(Mono.error(ex));
            })
        .onErrorMap(
            DataIntegrityViolationException.class,
            ex ->
                new BusinessException(
                    ErrorCode.USER_ALREADY_EXISTS, "Email is already registered"));
  }

  // ── Step 3 ────────────────────────────────────────────────────────────────

  /** Creates a 24-hour email verification token for the given user. */
  private Mono<reactor.util.function.Tuple2<AuthUser, String>> createVerificationToken(
      AuthUser savedUser) {
    String rawToken = otpGenerator.generate();
    Instant now = Instant.now();
    AuthVerificationToken verificationToken =
        AuthVerificationToken.builder()
            .userId(savedUser.getId())
            .token(rawToken)
            .expiresAt(now.plus(Duration.ofHours(24)))
            .createdAt(now)
            .build();
    return verificationTokenRepository
        .save(verificationToken)
        .thenReturn(Tuples.of(savedUser, rawToken));
  }

  // ── Step 4 ────────────────────────────────────────────────────────────────

  /** Writes {@code UserRegisteredEvent} and {@code EmailVerificationRequestedEvent} to outbox. */
  private Mono<Void> publishRegistrationEvents(
      AuthUser savedUser, String verificationToken, String traceId) {
    Instant now = Instant.now();
    String userId = String.valueOf(savedUser.getId());

    UserRegisteredEvent userEvent =
        UserRegisteredEvent.builder()
            .eventId(tsidIdGenerator.newId())
            .traceId(traceId)
            .occurredAt(now)
            .userId(userId)
            .email(savedUser.getEmail())
            .username(savedUser.getUsername())
            .build();

    EmailVerificationRequestedEvent emailEvent =
        EmailVerificationRequestedEvent.builder()
            .eventId(tsidIdGenerator.newId())
            .traceId(traceId)
            .occurredAt(now)
            .userId(userId)
            .email(savedUser.getEmail())
            .verificationToken(verificationToken)
            .build();

    return outboxPort
        .saveEvent(
            UserRegisteredEvent.EVENT_TYPE,
            KafkaTopic.USER_REGISTERED,
            AGGREGATE_TYPE,
            savedUser.getId(),
            userEvent,
            traceId)
        .then(
            outboxPort.saveEvent(
                EmailVerificationRequestedEvent.EVENT_TYPE,
                KafkaTopic.EMAIL_VERIFICATION,
                AGGREGATE_TYPE,
                savedUser.getId(),
                emailEvent,
                traceId));
  }

  // ── Other use cases ───────────────────────────────────────────────────────

  @Override
  public Mono<AuthTokenResult> loginUser(LoginCommand command) {
    return validateOrError(command)
        .then(
            userRepository
                .findByUsername(command.usernameOrEmail())
                .switchIfEmpty(userRepository.findByEmail(command.usernameOrEmail()))
                .switchIfEmpty(
                    Mono.error(
                        new BusinessException(ErrorCode.UNAUTHORIZED, "Invalid credentials")))
                .flatMap(
                    user -> {
                      if (user.getStatus() == AuthUserStatus.PENDING_VERIFICATION) {
                        return Mono.error(
                            new BusinessException(
                                ErrorCode.UNAUTHORIZED,
                                "Email not verified. Please check your inbox and verify your account."));
                      }
                      return identityProviderPort.loginUser(command);
                    }));
  }

  @Override
  @Transactional
  public Mono<Void> verifyEmail(String email, String token) {
    return verificationTokenRepository
        .findByToken(token)
        .switchIfEmpty(
            Mono.error(new BusinessException(ErrorCode.NOT_FOUND, "Invalid verification token")))
        .flatMap(
            vt -> {
              if (vt.isExpired()) {
                return Mono.error(
                    new BusinessException(ErrorCode.BAD_REQUEST, "Verification token has expired"));
              }
              if (vt.isUsed()) {
                return Mono.error(
                    new BusinessException(ErrorCode.BAD_REQUEST, "Token has already been used"));
              }
              vt.setUsedAt(Instant.now());
              return verificationTokenRepository.save(vt).thenReturn(vt.getUserId());
            })
        .flatMap(
            userId ->
                userRepository
                    .findById(userId)
                    .switchIfEmpty(
                        Mono.error(new BusinessException(ErrorCode.NOT_FOUND, "User not found")))
                    .flatMap(
                        user -> {
                          if (!email.equalsIgnoreCase(user.getEmail())) {
                            return Mono.error(
                                new BusinessException(
                                    ErrorCode.NOT_FOUND, "Invalid verification token"));
                          }
                          return identityProviderPort
                              .setEmailVerified(user.getKeycloakId(), true)
                              .then(
                                  Mono.defer(
                                      () -> {
                                        user.setStatus(AuthUserStatus.ACTIVE);
                                        return userRepository.save(user);
                                      }));
                        }))
        .then();
  }

  @Override
  public Mono<AuthTokenResult> refreshToken(String refreshToken) {
    return identityProviderPort.refreshToken(refreshToken);
  }

  @Override
  public Mono<Void> logout(String keycloakId) {
    return identityProviderPort.logout(keycloakId);
  }

  /**
   * Returns {@code Mono.error(BusinessException)} if validation fails, otherwise {@code
   * Mono.empty()} to signal the chain can continue.
   */
  private Mono<Void> validateOrError(Object command) {
    var errors = ZeabayValidator.validate(command);
    if (errors.isEmpty()) {
      return Mono.empty();
    }
    String message =
        "Validation failed: "
            + errors.stream()
                .map(e -> e.field() + " " + e.message())
                .reduce((a, b) -> a + ", " + b)
                .orElse("");
    return Mono.error(new BusinessException(ErrorCode.VALIDATION_ERROR, message));
  }
}
