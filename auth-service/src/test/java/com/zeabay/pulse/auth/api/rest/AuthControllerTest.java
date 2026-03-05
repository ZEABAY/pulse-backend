package com.zeabay.pulse.auth.api.rest;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

import com.zeabay.common.api.exception.BusinessException;
import com.zeabay.common.api.exception.ErrorCode;
import com.zeabay.common.web.ZeabayGlobalExceptionHandler;
import com.zeabay.pulse.auth.api.dto.AuthTokenApiResponse;
import com.zeabay.pulse.auth.api.dto.RegisterApiResponse;
import com.zeabay.pulse.auth.api.mapper.AuthMapper;
import com.zeabay.pulse.auth.application.dto.AuthTokenResult;
import com.zeabay.pulse.auth.application.dto.LoginCommand;
import com.zeabay.pulse.auth.application.dto.RegisterUserCommand;
import com.zeabay.pulse.auth.application.usecase.AuthService;
import com.zeabay.pulse.auth.domain.model.AuthUser;
import com.zeabay.pulse.auth.domain.model.AuthUserStatus;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.security.oauth2.server.resource.autoconfigure.reactive.ReactiveOAuth2ResourceServerAutoConfiguration;
import org.springframework.boot.webflux.test.autoconfigure.WebFluxTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.reactive.server.WebTestClient;
import reactor.core.publisher.Mono;

/**
 * HTTP katmanı slice testleri.
 *
 * <p>@WebFluxTest yalnızca controller + controller advice bean'lerini yükler; veritabanı, Kafka,
 * Keycloak bağlantısı gerektirmez. ZeabayGlobalExceptionHandler manuel olarak import edilir
 * çünkü @WebFluxTest, zeabay-webflux auto-configuration'ını içermez.
 */
@WebFluxTest(
    controllers = AuthController.class,
    excludeAutoConfiguration = ReactiveOAuth2ResourceServerAutoConfiguration.class)
@Import(ZeabayGlobalExceptionHandler.class)
class AuthControllerTest {

  @Autowired WebTestClient webClient;

  @MockitoBean AuthService authService;
  @MockitoBean AuthMapper authMapper;

  private AuthUser activeUser() {
    var user = new AuthUser();
    user.setId(817408902493679116L);
    user.setUsername("zeyneltest");
    user.setEmail("zeynel@zeabay.com");
    user.setStatus(AuthUserStatus.ACTIVE);
    return user;
  }

  // ─────────────────────────────────────────────────────────────
  //  POST /register
  // ─────────────────────────────────────────────────────────────

  @Nested
  class Register {

    @Test
    void success_returns201WithUserData() {
      when(authMapper.toRegisterCommand(any()))
          .thenReturn(new RegisterUserCommand("zeyneltest", "zeynel@zeabay.com", "Pass1234!"));
      when(authService.registerUser(any())).thenReturn(Mono.just(activeUser()));
      when(authMapper.toRegisterApiResponse(any()))
          .thenReturn(
              new RegisterApiResponse("817408902493679116", "zeyneltest", "zeynel@zeabay.com"));

      webClient
          .post()
          .uri("/api/v1/auth/register")
          .contentType(MediaType.APPLICATION_JSON)
          .bodyValue(
              """
              {"username":"zeyneltest","email":"zeynel@zeabay.com","password":"Pass1234!"}
              """)
          .exchange()
          .expectStatus()
          .isCreated()
          .expectBody()
          .jsonPath("$.success")
          .isEqualTo(true)
          .jsonPath("$.data.id")
          .isEqualTo("817408902493679116")
          .jsonPath("$.data.username")
          .isEqualTo("zeyneltest");
    }

    @Test
    void duplicateEmail_returns409() {
      when(authMapper.toRegisterCommand(any()))
          .thenReturn(new RegisterUserCommand("zeyneltest", "zeynel@zeabay.com", "Pass1234!"));
      when(authService.registerUser(any()))
          .thenReturn(
              Mono.error(
                  new BusinessException(
                      ErrorCode.USER_ALREADY_EXISTS, "Email is already registered")));

      webClient
          .post()
          .uri("/api/v1/auth/register")
          .contentType(MediaType.APPLICATION_JSON)
          .bodyValue(
              """
              {"username":"zeyneltest","email":"zeynel@zeabay.com","password":"Pass1234!"}
              """)
          .exchange()
          .expectStatus()
          .isEqualTo(409)
          .expectBody()
          .jsonPath("$.success")
          .isEqualTo(false)
          .jsonPath("$.error.code")
          .isEqualTo("USER_ALREADY_EXISTS");
    }

    @Test
    void invalidPayload_returns400WithValidationError() {
      webClient
          .post()
          .uri("/api/v1/auth/register")
          .contentType(MediaType.APPLICATION_JSON)
          .bodyValue(
              """
              {"username":"ab","email":"not-valid-email","password":"Pass1234!"}
              """)
          .exchange()
          .expectStatus()
          .isBadRequest()
          .expectBody()
          .jsonPath("$.success")
          .isEqualTo(false)
          .jsonPath("$.error.code")
          .isEqualTo("VALIDATION_ERROR");
    }
  }

  // ─────────────────────────────────────────────────────────────
  //  POST /login
  // ─────────────────────────────────────────────────────────────

  @Nested
  class Login {

    @Test
    void success_returns200WithTokens() {
      when(authMapper.toLoginCommand(any()))
          .thenReturn(new LoginCommand("zeyneltest", "Pass1234!"));
      when(authService.loginUser(any()))
          .thenReturn(Mono.just(new AuthTokenResult("access.token", "refresh.token", 300)));
      when(authMapper.toTokenApiResponse(any()))
          .thenReturn(new AuthTokenApiResponse("access.token", "refresh.token", 300));

      webClient
          .post()
          .uri("/api/v1/auth/login")
          .contentType(MediaType.APPLICATION_JSON)
          .bodyValue(
              """
              {"username":"zeyneltest","password":"Pass1234!"}
              """)
          .exchange()
          .expectStatus()
          .isOk()
          .expectBody()
          .jsonPath("$.success")
          .isEqualTo(true)
          .jsonPath("$.data.accessToken")
          .isEqualTo("access.token")
          .jsonPath("$.data.expiresIn")
          .isEqualTo(300);
    }

    @Test
    void invalidCredentials_returns401() {
      when(authMapper.toLoginCommand(any())).thenReturn(new LoginCommand("zeyneltest", "wrong"));
      when(authService.loginUser(any()))
          .thenReturn(
              Mono.error(new BusinessException(ErrorCode.UNAUTHORIZED, "Invalid credentials")));

      webClient
          .post()
          .uri("/api/v1/auth/login")
          .contentType(MediaType.APPLICATION_JSON)
          .bodyValue(
              """
              {"username":"zeyneltest","password":"wrong"}
              """)
          .exchange()
          .expectStatus()
          .isUnauthorized()
          .expectBody()
          .jsonPath("$.error.code")
          .isEqualTo("UNAUTHORIZED");
    }
  }

  // ─────────────────────────────────────────────────────────────
  //  GET /verify
  // ─────────────────────────────────────────────────────────────

  @Nested
  class VerifyEmail {

    @Test
    void validToken_returns200() {
      when(authService.verifyEmail("valid-token")).thenReturn(Mono.empty());

      webClient
          .get()
          .uri("/api/v1/auth/verify?token=valid-token")
          .exchange()
          .expectStatus()
          .isOk()
          .expectBody()
          .jsonPath("$.success")
          .isEqualTo(true)
          .jsonPath("$.data")
          .isEqualTo("Email verified successfully");
    }

    @Test
    void unknownToken_returns404() {
      when(authService.verifyEmail("bad-token"))
          .thenReturn(
              Mono.error(new BusinessException(ErrorCode.NOT_FOUND, "Invalid verification token")));

      webClient
          .get()
          .uri("/api/v1/auth/verify?token=bad-token")
          .exchange()
          .expectStatus()
          .isNotFound()
          .expectBody()
          .jsonPath("$.error.code")
          .isEqualTo("NOT_FOUND");
    }

    @Test
    void expiredToken_returns400() {
      when(authService.verifyEmail("expired-token"))
          .thenReturn(
              Mono.error(
                  new BusinessException(ErrorCode.BAD_REQUEST, "Verification token has expired")));

      webClient
          .get()
          .uri("/api/v1/auth/verify?token=expired-token")
          .exchange()
          .expectStatus()
          .isBadRequest()
          .expectBody()
          .jsonPath("$.error.code")
          .isEqualTo("BAD_REQUEST");
    }
  }

  // ─────────────────────────────────────────────────────────────
  //  POST /refresh
  // ─────────────────────────────────────────────────────────────

  @Nested
  class Refresh {

    @Test
    void invalidRefreshToken_returns401() {
      when(authService.refreshToken(anyString()))
          .thenReturn(
              Mono.error(
                  new BusinessException(
                      ErrorCode.UNAUTHORIZED, "Invalid or expired refresh token")));

      webClient
          .post()
          .uri("/api/v1/auth/refresh")
          .contentType(MediaType.APPLICATION_JSON)
          .bodyValue(
              """
              {"refreshToken":"expired.token"}
              """)
          .exchange()
          .expectStatus()
          .isUnauthorized()
          .expectBody()
          .jsonPath("$.error.code")
          .isEqualTo("UNAUTHORIZED");
    }
  }

  // ─────────────────────────────────────────────────────────────
  //  POST /logout
  // ─────────────────────────────────────────────────────────────

  @Nested
  class Logout {

    @Test
    void returns204() {
      webClient.post().uri("/api/v1/auth/logout").exchange().expectStatus().isNoContent();
    }
  }
}
