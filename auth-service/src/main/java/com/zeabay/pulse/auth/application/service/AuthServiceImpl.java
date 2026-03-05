package com.zeabay.pulse.auth.application.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.f4b6a3.tsid.TsidCreator;
import com.zeabay.common.api.exception.BusinessException;
import com.zeabay.common.api.exception.ErrorCode;
import com.zeabay.common.constant.ZeabayConstants;
import com.zeabay.common.kafka.event.auth.EmailVerificationRequestedEvent;
import com.zeabay.common.kafka.event.user.UserRegisteredEvent;
import com.zeabay.common.logging.Loggable;
import com.zeabay.common.outbox.OutboxEvent;
import com.zeabay.common.outbox.OutboxEventRepository;
import com.zeabay.common.validation.ZeabayValidator;
import com.zeabay.pulse.auth.application.dto.AuthTokenResult;
import com.zeabay.pulse.auth.application.dto.LoginCommand;
import com.zeabay.pulse.auth.application.dto.RegisterUserCommand;
import com.zeabay.pulse.auth.application.port.IdentityProviderPort;
import com.zeabay.pulse.auth.application.usecase.AuthService;
import com.zeabay.pulse.auth.domain.model.AuthUser;
import com.zeabay.pulse.auth.domain.model.AuthUserStatus;
import com.zeabay.pulse.auth.domain.model.AuthVerificationToken;
import com.zeabay.pulse.auth.domain.repository.AuthUserRepository;
import com.zeabay.pulse.auth.domain.repository.AuthVerificationTokenRepository;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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

  private final AuthUserRepository userRepository;
  private final AuthVerificationTokenRepository verificationTokenRepository;
  private final IdentityProviderPort identityProviderPort;
  private final OutboxEventRepository outboxEventRepository;
  private final ObjectMapper objectMapper;

  @Override
  @Transactional
  public Mono<AuthUser> registerUser(RegisterUserCommand command) {
    var errors = ZeabayValidator.validate(command);
    if (!errors.isEmpty()) {
      return Mono.error(
          new BusinessException(
              ErrorCode.VALIDATION_ERROR,
              "Validation failed: "
                  + errors.stream()
                      .map(e -> e.field() + " " + e.message())
                      .reduce((a, b) -> a + ", " + b)
                      .orElse("")));
    }

    return Mono.deferContextual(
        ctx -> {
          String traceId = ctx.getOrDefault(ZeabayConstants.TRACE_ID_CTX_KEY, "");

          return userRepository
              .findByEmail(command.email())
              .hasElement()
              .flatMap(
                  exists -> {
                    if (exists) {
                      return Mono.error(
                          new BusinessException(
                              ErrorCode.USER_ALREADY_EXISTS, "Email is already registered"));
                    }
                    return Mono.just(false);
                  })
              .then(identityProviderPort.registerUser(command))
              .flatMap(
                  keycloakId -> {
                    AuthUser newUser =
                        AuthUser.builder()
                            .keycloakId(keycloakId)
                            .username(command.username())
                            .email(command.email())
                            .build();
                    return userRepository.save(newUser);
                  })
              .flatMap(
                  savedUser ->
                      identityProviderPort
                          .assignRole(savedUser.getKeycloakId(), DEFAULT_ROLE)
                          .thenReturn(savedUser))
              .flatMap(
                  savedUser -> {
                    String rawToken = UUID.randomUUID().toString().replace("-", "");
                    AuthVerificationToken verificationToken =
                        AuthVerificationToken.builder()
                            .userId(savedUser.getId())
                            .token(rawToken)
                            .expiresAt(Instant.now().plus(Duration.ofHours(24)))
                            .createdAt(Instant.now())
                            .build();
                    return verificationTokenRepository
                        .save(verificationToken)
                        .map(saved -> Tuples.of(savedUser, rawToken));
                  })
              .flatMap(
                  tuple -> {
                    AuthUser savedUser = tuple.getT1();
                    String verificationToken = tuple.getT2();
                    try {
                      String userEventJson =
                          objectMapper.writeValueAsString(
                              UserRegisteredEvent.builder()
                                  .eventId(TsidCreator.getTsid().toString())
                                  .traceId(traceId)
                                  .occurredAt(Instant.now())
                                  .userId(String.valueOf(savedUser.getId()))
                                  .email(savedUser.getEmail())
                                  .username(savedUser.getUsername())
                                  .build());

                      String emailEventJson =
                          objectMapper.writeValueAsString(
                              EmailVerificationRequestedEvent.builder()
                                  .eventId(TsidCreator.getTsid().toString())
                                  .traceId(traceId)
                                  .occurredAt(Instant.now())
                                  .userId(String.valueOf(savedUser.getId()))
                                  .email(savedUser.getEmail())
                                  .verificationToken(verificationToken)
                                  .build());

                      OutboxEvent userOutboxEvent =
                          OutboxEvent.builder()
                              .eventType(UserRegisteredEvent.EVENT_TYPE)
                              .topic("pulse.auth.user-registered")
                              .aggregateType("AuthUser")
                              .aggregateId(savedUser.getId())
                              .payload(userEventJson)
                              .traceId(traceId)
                              .status(OutboxEvent.Status.PENDING)
                              .retryCount(0)
                              .build();

                      OutboxEvent emailOutboxEvent =
                          OutboxEvent.builder()
                              .eventType(EmailVerificationRequestedEvent.EVENT_TYPE)
                              .topic("pulse.auth.email-verification")
                              .aggregateType("AuthUser")
                              .aggregateId(savedUser.getId())
                              .payload(emailEventJson)
                              .traceId(traceId)
                              .status(OutboxEvent.Status.PENDING)
                              .retryCount(0)
                              .build();

                      return outboxEventRepository
                          .save(userOutboxEvent)
                          .then(outboxEventRepository.save(emailOutboxEvent))
                          .thenReturn(savedUser);
                    } catch (Exception e) {
                      return Mono.error(
                          new RuntimeException("Failed to serialize outbox event", e));
                    }
                  });
        });
  }

  @Override
  public Mono<AuthTokenResult> loginUser(LoginCommand command) {
    var errors = ZeabayValidator.validate(command);
    if (!errors.isEmpty()) {
      return Mono.error(
          new BusinessException(
              ErrorCode.VALIDATION_ERROR,
              "Validation failed: "
                  + errors.stream()
                      .map(e -> e.field() + " " + e.message())
                      .reduce((a, b) -> a + ", " + b)
                      .orElse("")));
    }
    return userRepository
        .findByUsername(command.usernameOrEmail())
        .switchIfEmpty(userRepository.findByEmail(command.usernameOrEmail()))
        .switchIfEmpty(
            Mono.error(new BusinessException(ErrorCode.UNAUTHORIZED, "Invalid credentials")))
        .flatMap(
            user -> {
              if (user.getStatus() == AuthUserStatus.PENDING_VERIFICATION) {
                return Mono.error(
                    new BusinessException(
                        ErrorCode.UNAUTHORIZED,
                        "Email not verified. Please check your inbox and verify your account."));
              }
              return identityProviderPort.loginUser(command);
            });
  }

  @Override
  @Transactional
  public Mono<Void> verifyEmail(String token) {
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
              return verificationTokenRepository.save(vt).map(saved -> vt.getUserId());
            })
        .flatMap(
            userId ->
                userRepository
                    .findById(userId)
                    .switchIfEmpty(
                        Mono.error(new BusinessException(ErrorCode.NOT_FOUND, "User not found"))))
        .flatMap(
            user ->
                identityProviderPort
                    .setEmailVerified(user.getKeycloakId(), true)
                    .then(
                        Mono.defer(
                            () -> {
                              user.setStatus(AuthUserStatus.ACTIVE);
                              return userRepository.save(user);
                            })))
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
}
