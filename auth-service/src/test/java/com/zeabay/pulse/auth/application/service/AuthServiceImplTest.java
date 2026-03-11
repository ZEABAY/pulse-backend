package com.zeabay.pulse.auth.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.zeabay.common.api.exception.BusinessException;
import com.zeabay.common.api.exception.ErrorCode;
import com.zeabay.common.security.OtpGenerator;
import com.zeabay.common.tsid.TsidGenerator;
import com.zeabay.pulse.auth.application.dto.AuthTokenResult;
import com.zeabay.pulse.auth.application.dto.LoginCommand;
import com.zeabay.pulse.auth.application.dto.RegisterUserCommand;
import com.zeabay.pulse.auth.application.port.IdentityProviderPort;
import com.zeabay.pulse.auth.application.port.out.OutboxPort;
import com.zeabay.pulse.auth.domain.model.AuthUser;
import com.zeabay.pulse.auth.domain.model.AuthUserStatus;
import com.zeabay.pulse.auth.domain.model.AuthUserTestFixtures;
import com.zeabay.pulse.auth.domain.model.AuthVerificationToken;
import com.zeabay.pulse.auth.domain.repository.AuthUserRepository;
import com.zeabay.pulse.auth.domain.repository.AuthVerificationTokenRepository;
import java.time.Instant;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

@ExtendWith(MockitoExtension.class)
class AuthServiceImplTest {

  @Mock AuthUserRepository userRepository;
  @Mock AuthVerificationTokenRepository verificationTokenRepository;
  @Mock IdentityProviderPort identityProviderPort;
  @Mock OutboxPort outboxPort;
  @Mock OtpGenerator otpGenerator;
  @Mock TsidGenerator tsidGenerator;

  @InjectMocks AuthServiceImpl service;

  private AuthUser activeUser() {
    return AuthUserTestFixtures.withId(
        100L, "kc-uuid", "zeyneltest", "zeynel@test.com", AuthUserStatus.ACTIVE);
  }

  // ─────────────────────────────────────────────────────────────
  //  registerUser
  // ─────────────────────────────────────────────────────────────

  @Nested
  class RegisterUser {

    @Test
    void success_savesUserAndReturnsIt() throws Exception {
      var command = new RegisterUserCommand("zeyneltest", "zeynel@test.com", "Pass1234!");
      var savedUser = activeUser();

      when(userRepository.findByEmail("zeynel@test.com")).thenReturn(Mono.empty());
      when(identityProviderPort.registerUser(any())).thenReturn(Mono.just("kc-uuid"));
      when(userRepository.save(any())).thenReturn(Mono.just(savedUser));
      when(identityProviderPort.assignRole(any(), any())).thenReturn(Mono.empty());
      when(verificationTokenRepository.save(any()))
          .thenReturn(Mono.just(new AuthVerificationToken()));
      when(otpGenerator.generate()).thenReturn("123456");
      when(tsidGenerator.newId()).thenReturn("test-tsid-id");
      when(outboxPort.saveEvent(any(), any(), any(), any(), any(), any())).thenReturn(Mono.empty());

      StepVerifier.create(service.registerUser(command))
          .assertNext(user -> assertThat(user.getEmail()).isEqualTo("zeynel@test.com"))
          .verifyComplete();
    }

    @Test
    void duplicateEmail_throwsUserAlreadyExists() {
      var command = new RegisterUserCommand("zeyneltest", "zeynel@test.com", "Pass1234!");

      when(userRepository.findByEmail("zeynel@test.com")).thenReturn(Mono.just(activeUser()));
      // .then(identityProviderPort.registerUser(...)) evaluates argument eagerly — must be non-null
      // even though it won't subscribe
      when(identityProviderPort.registerUser(any())).thenReturn(Mono.empty());

      StepVerifier.create(service.registerUser(command))
          .expectErrorSatisfies(
              ex -> {
                assertThat(ex).isInstanceOf(BusinessException.class);
                assertThat(((BusinessException) ex).getErrorCode())
                    .isEqualTo(ErrorCode.USER_ALREADY_EXISTS);
              })
          .verify();
    }

    @Test
    void dbSaveFails_deletesKeycloakUserAndPropagatesError() {
      var command = new RegisterUserCommand("zeyneltest", "zeynel@test.com", "Pass1234!");
      var dbError = new RuntimeException("DB connection lost");

      when(userRepository.findByEmail("zeynel@test.com")).thenReturn(Mono.empty());
      when(identityProviderPort.registerUser(any())).thenReturn(Mono.just("kc-uuid"));
      when(userRepository.save(any())).thenReturn(Mono.error(dbError));
      when(identityProviderPort.deleteUser(eq("kc-uuid"))).thenReturn(Mono.empty());

      StepVerifier.create(service.registerUser(command))
          .expectErrorSatisfies(ex -> assertThat(ex).isEqualTo(dbError))
          .verify();

      verify(identityProviderPort).deleteUser("kc-uuid");
    }

    @Test
    void dbSaveConstraintViolation_deletesKeycloakUserAndThrowsUserAlreadyExists() {
      var command = new RegisterUserCommand("zeyneltest", "zeynel@test.com", "Pass1234!");

      when(userRepository.findByEmail("zeynel@test.com")).thenReturn(Mono.empty());
      when(identityProviderPort.registerUser(any())).thenReturn(Mono.just("kc-uuid"));
      when(userRepository.save(any()))
          .thenReturn(Mono.error(new DataIntegrityViolationException("duplicate key")));
      when(identityProviderPort.deleteUser(eq("kc-uuid"))).thenReturn(Mono.empty());

      StepVerifier.create(service.registerUser(command))
          .expectErrorSatisfies(
              ex -> {
                assertThat(ex).isInstanceOf(BusinessException.class);
                assertThat(((BusinessException) ex).getErrorCode())
                    .isEqualTo(ErrorCode.USER_ALREADY_EXISTS);
              })
          .verify();

      verify(identityProviderPort).deleteUser("kc-uuid");
    }
  }

  // ─────────────────────────────────────────────────────────────
  //  loginUser
  // ─────────────────────────────────────────────────────────────

  @Nested
  class LoginUser {

    @Test
    void unknownUser_throwsUnauthorized() {
      var command = new LoginCommand("unknown", "Pass1234!");

      when(userRepository.findByUsername("unknown")).thenReturn(Mono.empty());
      when(userRepository.findByEmail("unknown")).thenReturn(Mono.empty());

      StepVerifier.create(service.loginUser(command))
          .expectErrorSatisfies(
              ex -> {
                assertThat(ex).isInstanceOf(BusinessException.class);
                assertThat(((BusinessException) ex).getErrorCode())
                    .isEqualTo(ErrorCode.UNAUTHORIZED);
                assertThat(ex.getMessage()).isEqualTo("Invalid credentials");
              })
          .verify();
    }

    @Test
    void pendingVerificationUser_throwsUnauthorized() {
      var command = new LoginCommand("zeyneltest", "Pass1234!");
      var pendingUser =
          AuthUserTestFixtures.withId(
              100L, null, "zeyneltest", "zeynel@test.com", AuthUserStatus.PENDING_VERIFICATION);

      when(userRepository.findByUsername("zeyneltest")).thenReturn(Mono.just(pendingUser));
      // switchIfEmpty evaluates argument eagerly — must be non-null even though it won't subscribe
      when(userRepository.findByEmail("zeyneltest")).thenReturn(Mono.empty());

      StepVerifier.create(service.loginUser(command))
          .expectErrorSatisfies(
              ex -> {
                assertThat(ex).isInstanceOf(BusinessException.class);
                assertThat(((BusinessException) ex).getErrorCode())
                    .isEqualTo(ErrorCode.UNAUTHORIZED);
                assertThat(ex.getMessage()).contains("Email not verified");
              })
          .verify();
    }

    @Test
    void activeUser_delegatesToIdentityProvider() {
      var command = new LoginCommand("zeyneltest", "Pass1234!");
      var tokenResult = new AuthTokenResult("access", "refresh", 300);

      when(userRepository.findByUsername("zeyneltest")).thenReturn(Mono.just(activeUser()));
      when(userRepository.findByEmail("zeyneltest")).thenReturn(Mono.empty());
      when(identityProviderPort.loginUser(command)).thenReturn(Mono.just(tokenResult));

      StepVerifier.create(service.loginUser(command))
          .assertNext(result -> assertThat(result.accessToken()).isEqualTo("access"))
          .verifyComplete();
    }
  }

  // ─────────────────────────────────────────────────────────────
  //  verifyEmail
  // ─────────────────────────────────────────────────────────────

  @Nested
  class VerifyEmail {

    @Test
    void unknownToken_throwsNotFound() {
      when(verificationTokenRepository.findByToken("bad-token")).thenReturn(Mono.empty());

      StepVerifier.create(service.verifyEmail("zeynel@test.com", "bad-token"))
          .expectErrorSatisfies(
              ex -> {
                assertThat(ex).isInstanceOf(BusinessException.class);
                assertThat(((BusinessException) ex).getErrorCode()).isEqualTo(ErrorCode.NOT_FOUND);
              })
          .verify();
    }

    @Test
    void expiredToken_throwsBadRequest() {
      var expired =
          AuthVerificationToken.builder()
              .token("expired-token")
              .userId(100L)
              .expiresAt(Instant.now().minusSeconds(1))
              .build();

      when(verificationTokenRepository.findByToken("expired-token")).thenReturn(Mono.just(expired));

      StepVerifier.create(service.verifyEmail("zeynel@test.com", "expired-token"))
          .expectErrorSatisfies(
              ex -> {
                assertThat(ex).isInstanceOf(BusinessException.class);
                assertThat(((BusinessException) ex).getErrorCode())
                    .isEqualTo(ErrorCode.BAD_REQUEST);
                assertThat(ex.getMessage()).contains("expired");
              })
          .verify();
    }

    @Test
    void usedToken_throwsBadRequest() {
      var used =
          AuthVerificationToken.builder()
              .token("used-token")
              .userId(100L)
              .expiresAt(Instant.now().plusSeconds(3600))
              .usedAt(Instant.now().minusSeconds(60))
              .build();

      when(verificationTokenRepository.findByToken("used-token")).thenReturn(Mono.just(used));

      StepVerifier.create(service.verifyEmail("zeynel@test.com", "used-token"))
          .expectErrorSatisfies(
              ex -> {
                assertThat(ex).isInstanceOf(BusinessException.class);
                assertThat(((BusinessException) ex).getErrorCode())
                    .isEqualTo(ErrorCode.BAD_REQUEST);
                assertThat(ex.getMessage()).contains("used");
              })
          .verify();
    }

    @Test
    void wrongEmail_throwsNotFound() {
      var validToken =
          AuthVerificationToken.builder()
              .token("123456")
              .userId(100L)
              .expiresAt(Instant.now().plusSeconds(3600))
              .build();
      var user = activeUser(); // email = "zeynel@test.com"

      when(verificationTokenRepository.findByToken("123456")).thenReturn(Mono.just(validToken));
      when(verificationTokenRepository.save(any())).thenReturn(Mono.just(validToken));
      when(userRepository.findById(100L)).thenReturn(Mono.just(user));

      StepVerifier.create(service.verifyEmail("wrong@email.com", "123456"))
          .expectErrorSatisfies(
              ex -> {
                assertThat(ex).isInstanceOf(BusinessException.class);
                assertThat(((BusinessException) ex).getErrorCode()).isEqualTo(ErrorCode.NOT_FOUND);
              })
          .verify();
    }
  }
}
