package com.zeabay.pulse.auth.application.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.f4b6a3.tsid.TsidCreator;
import com.zeabay.common.api.exception.BusinessException;
import com.zeabay.common.api.exception.ErrorCode;
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
import com.zeabay.pulse.auth.domain.repository.AuthUserRepository;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import reactor.core.publisher.Mono;

@Slf4j
@Service
@Loggable
@RequiredArgsConstructor
public class AuthServiceImpl implements AuthService {

  private final AuthUserRepository userRepository;
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
                      .roles(Set.of("ROLE_USER"))
                      .build();

              return userRepository
                  .save(newUser)
                  .flatMap(
                      savedUser -> {
                        var eventPayload =
                            UserRegisteredEvent.builder()
                                .eventId(TsidCreator.getTsid().toString())
                                .traceId(null)
                                .occurredAt(java.time.Instant.now())
                                .userId(String.valueOf(savedUser.getId()))
                                .email(savedUser.getEmail())
                                .username(savedUser.getUsername())
                                .build();

                        try {
                          String payloadJson = objectMapper.writeValueAsString(eventPayload);
                          var outboxEvent =
                              OutboxEvent.builder()
                                  .eventType(UserRegisteredEvent.EVENT_TYPE)
                                  .topic("pulse.auth.user-registered")
                                  .aggregateType("AuthUser")
                                  .aggregateId(savedUser.getId())
                                  .payload(payloadJson)
                                  .traceId(null)
                                  .status(OutboxEvent.Status.PENDING)
                                  .retryCount(0)
                                  .build();

                          return outboxEventRepository.save(outboxEvent).thenReturn(savedUser);
                        } catch (Exception e) {
                          return Mono.error(
                              new RuntimeException("Failed to serialize Outbox event", e));
                        }
                      });
            });
  }

  @Override
  public Mono<AuthTokenResult> loginUser(LoginCommand command) {
    var errors = com.zeabay.common.validation.ZeabayValidator.validate(command);
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

    return identityProviderPort.loginUser(command);
  }
}
