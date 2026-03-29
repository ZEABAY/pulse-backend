package com.zeabay.pulse.auth.application.service;

import com.zeabay.common.api.exception.BusinessException;
import com.zeabay.common.api.exception.ErrorCode;
import com.zeabay.common.constant.ZeabayConstants;
import com.zeabay.common.logging.Loggable;
import com.zeabay.common.security.OtpGenerator;
import com.zeabay.common.tsid.TsidGenerator;
import com.zeabay.common.validation.ZeabayValidator;
import com.zeabay.pulse.auth.application.dto.AuthTokenResult;
import com.zeabay.pulse.auth.application.dto.LoginCommand;
import com.zeabay.pulse.auth.application.dto.RegisterUserCommand;
import com.zeabay.pulse.auth.application.port.IdentityProviderPort;
import com.zeabay.pulse.auth.application.port.TokenBlacklistPort;
import com.zeabay.pulse.auth.application.port.out.OutboxPort;
import com.zeabay.pulse.auth.application.usecase.AuthService;
import com.zeabay.pulse.auth.domain.model.AuthPasswordResetToken;
import com.zeabay.pulse.auth.domain.model.AuthUser;
import com.zeabay.pulse.auth.domain.model.AuthUserStatus;
import com.zeabay.pulse.auth.domain.model.AuthVerificationToken;
import com.zeabay.pulse.auth.domain.repository.AuthPasswordResetTokenRepository;
import com.zeabay.pulse.auth.domain.repository.AuthUserRepository;
import com.zeabay.pulse.auth.domain.repository.AuthVerificationTokenRepository;
import com.zeabay.pulse.shared.events.auth.EmailVerificationRequestedEvent;
import com.zeabay.pulse.shared.events.auth.PasswordResetRequestedEvent;
import com.zeabay.pulse.shared.kafka.PulseTopics;
import java.time.Duration;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import reactor.core.publisher.Mono;
import reactor.util.context.ContextView;

@Slf4j
@Service
@Loggable
@RequiredArgsConstructor
public class AuthServiceImpl implements AuthService {
  private static final String DEFAULT_ROLE = "user";
  private static final String AGGREGATE_TYPE = "AuthUser";

  private final AuthUserRepository userRepository;
  private final AuthVerificationTokenRepository verificationTokenRepository;
  private final AuthPasswordResetTokenRepository passwordResetTokenRepository;
  private final IdentityProviderPort identityProviderPort;
  private final TokenBlacklistPort tokenBlacklistPort;
  private final OutboxPort outboxPort;

  private final OtpGenerator otpGenerator;
  private final TsidGenerator tsidGenerator;

  @Override
  @Transactional
  public Mono<AuthUser> registerUser(RegisterUserCommand command) {
    return validateOrError(command)
        .then(Mono.deferContextual(ctx -> performRegistrationFlow(command, ctx)));
  }

  private Mono<AuthUser> performRegistrationFlow(RegisterUserCommand command, ContextView ctx) {
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
                        verificationToken ->
                            publishRegistrationEvents(
                                    savedUser, verificationToken.getToken(), traceId)
                                .thenReturn(savedUser)));
  }

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

  /**
   * Saves the new user. If DB save fails, rolls back the Keycloak user as a compensating action.
   * {@link DataIntegrityViolationException} (race condition) is mapped to {@link
   * ErrorCode#USER_ALREADY_EXISTS}.
   */
  private Mono<AuthUser> createAndPersistUser(
      String keycloakId, RegisterUserCommand command, String traceId) {
    AuthUser newUser = buildAuthUserFromCommand(keycloakId, command);
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
            _ ->
                new BusinessException(
                    ErrorCode.USER_ALREADY_EXISTS, "Email is already registered"));
  }

  private AuthUser buildAuthUserFromCommand(String keycloakId, RegisterUserCommand command) {
    return AuthUser.builder()
        .keycloakId(keycloakId)
        .username(command.username())
        .email(command.email())
        .build();
  }

  /** Creates a 24-hour email verification token for the given user. */
  private Mono<AuthVerificationToken> createVerificationToken(AuthUser savedUser) {
    String rawToken = otpGenerator.generate();
    Instant now = Instant.now();
    AuthVerificationToken verificationToken =
        AuthVerificationToken.builder()
            .userId(savedUser.getId())
            .token(rawToken)
            .expiresAt(now.plus(Duration.ofHours(24)))
            .createdAt(now)
            .build();
    return verificationTokenRepository.save(verificationToken);
  }

  /** Writes {@code EmailVerificationRequestedEvent} to outbox for mail-service. */
  private Mono<Void> publishRegistrationEvents(
      AuthUser savedUser, String verificationToken, String traceId) {
    Instant now = Instant.now();
    EmailVerificationRequestedEvent emailEvent =
        buildEmailVerificationEvent(savedUser, verificationToken, traceId, now);
    return outboxPort.saveEvent(
        emailEvent.getEventId(),
        EmailVerificationRequestedEvent.EVENT_TYPE,
        PulseTopics.EMAIL_VERIFICATION,
        AGGREGATE_TYPE,
        savedUser.getId(),
        emailEvent,
        traceId);
  }

  private EmailVerificationRequestedEvent buildEmailVerificationEvent(
      AuthUser user, String token, String traceId, Instant now) {
    return EmailVerificationRequestedEvent.builder()
        .eventId(tsidGenerator.newId())
        .traceId(traceId)
        .occurredAt(now)
        .userId(String.valueOf(user.getId()))
        .email(user.getEmail())
        .verificationToken(token)
        .build();
  }

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
                                ErrorCode.EMAIL_NOT_VERIFIED,
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
        .flatMap(this::validateToken)
        .flatMap(
            vt ->
                userRepository
                    .findById(vt.getUserId())
                    .switchIfEmpty(Mono.error(new BusinessException(ErrorCode.USER_NOT_FOUND)))
                    .flatMap(user -> markTokenUsedAndActivateUser(vt, user, email)))
        .flatMap(
            savedUser -> identityProviderPort.setEmailVerified(savedUser.getKeycloakId(), true))
        .then();
  }

  private Mono<AuthVerificationToken> validateToken(AuthVerificationToken vt) {
    if (vt.isExpired()) {
      return Mono.error(new BusinessException(ErrorCode.BAD_REQUEST, "Token expired"));
    }
    if (vt.isUsed()) {
      return Mono.error(new BusinessException(ErrorCode.BAD_REQUEST, "Token used"));
    }
    return Mono.just(vt);
  }

  /** Marks verification token as used and sets user status to ACTIVE. */
  private Mono<AuthUser> markTokenUsedAndActivateUser(
      AuthVerificationToken vt, AuthUser user, String email) {
    if (!email.equalsIgnoreCase(user.getEmail())) {
      return Mono.error(new BusinessException(ErrorCode.NOT_FOUND, "Invalid token"));
    }
    vt.setUsedAt(Instant.now());
    return verificationTokenRepository
        .save(vt)
        .then(
            Mono.defer(
                () -> {
                  user.setStatus(AuthUserStatus.ACTIVE);
                  return userRepository.save(user);
                }));
  }

  @Override
  public Mono<AuthTokenResult> refreshToken(String refreshToken) {
    return identityProviderPort.refreshToken(refreshToken);
  }

  @Override
  @Transactional
  public Mono<Void> forgotPassword(String email) {
    return Mono.deferContextual(
        ctx -> {
          String traceId = ctx.getOrDefault(ZeabayConstants.TRACE_ID_CTX_KEY, "");
          return userRepository
              .findByEmail(email)
              .flatMap(
                  user -> {
                    Instant now = Instant.now();
                    return passwordResetTokenRepository
                        .invalidatePreviousTokensForUser(user.getId(), now)
                        .then(
                            Mono.defer(
                                () -> {
                                  String token = otpGenerator.generate();
                                  AuthPasswordResetToken resetToken =
                                      AuthPasswordResetToken.builder()
                                          .userId(user.getId())
                                          .token(token)
                                          .expiresAt(now.plus(Duration.ofMinutes(15)))
                                          .createdAt(now)
                                          .build();

                                  return passwordResetTokenRepository
                                      .save(resetToken)
                                      .flatMap(
                                          _ ->
                                              publishPasswordResetEvent(user, token, traceId, now));
                                }));
                  })
              .then();
        });
  }

  private Mono<Void> publishPasswordResetEvent(
      AuthUser user, String token, String traceId, Instant now) {
    PasswordResetRequestedEvent event =
        PasswordResetRequestedEvent.builder()
            .eventId(tsidGenerator.newId())
            .traceId(traceId)
            .occurredAt(now)
            .userId(String.valueOf(user.getId()))
            .email(user.getEmail())
            .resetToken(token)
            .build();
    return outboxPort.saveEvent(
        event.getEventId(),
        PasswordResetRequestedEvent.EVENT_TYPE,
        PulseTopics.PASSWORD_RESET,
        AGGREGATE_TYPE,
        user.getId(),
        event,
        traceId);
  }

  @Override
  public Mono<Void> verifyResetOtp(String email, String otp) {
    return userRepository
        .findByEmail(email)
        .flatMap(user -> passwordResetTokenRepository.findByUserIdAndToken(user.getId(), otp))
        .flatMap(
            rt -> {
              if (rt.isExpired()) {
                return Mono.error(new BusinessException(ErrorCode.BAD_REQUEST, "Token expired"));
              }
              if (rt.isUsed()) {
                return Mono.error(
                    new BusinessException(ErrorCode.BAD_REQUEST, "Token already used"));
              }
              return Mono.just(true); // Signal success
            })
        .switchIfEmpty(
            Mono.error(new BusinessException(ErrorCode.NOT_FOUND, "Invalid or expired token")))
        .then();
  }

  @Override
  @Transactional
  public Mono<Void> resetPassword(String email, String otp, String newPassword) {
    return userRepository
        .findByEmail(email)
        .flatMap(
            user ->
                passwordResetTokenRepository
                    .findByUserIdAndToken(user.getId(), otp)
                    .flatMap(
                        rt -> {
                          if (rt.isExpired()) {
                            return Mono.error(
                                new BusinessException(ErrorCode.BAD_REQUEST, "Token expired"));
                          }
                          if (rt.isUsed()) {
                            return Mono.error(
                                new BusinessException(ErrorCode.BAD_REQUEST, "Token already used"));
                          }
                          rt.setUsedAt(Instant.now());
                          return passwordResetTokenRepository
                              .save(rt)
                              .then(
                                  identityProviderPort.resetPassword(
                                      user.getKeycloakId(), newPassword))
                              .thenReturn(true);
                        }))
        .switchIfEmpty(
            Mono.error(new BusinessException(ErrorCode.NOT_FOUND, "Invalid or expired token")))
        .then();
  }

  @Override
  public Mono<Void> logout(String keycloakId, String jti, long ttlSeconds) {
    return tokenBlacklistPort
        .isBlacklisted(jti)
        .flatMap(
            blacklisted -> {
              if (blacklisted) {
                return Mono.empty();
              }
              return tokenBlacklistPort
                  .add(jti, ttlSeconds)
                  .then(identityProviderPort.logout(keycloakId));
            });
  }

  private Mono<Void> validateOrError(Object command) {
    var errors = ZeabayValidator.validate(command);
    if (errors.isEmpty()) {
      return Mono.empty();
    }
    String message = "Validation failed: " + ZeabayValidator.formatErrors(errors);
    return Mono.error(new BusinessException(ErrorCode.VALIDATION_ERROR, message));
  }
}
