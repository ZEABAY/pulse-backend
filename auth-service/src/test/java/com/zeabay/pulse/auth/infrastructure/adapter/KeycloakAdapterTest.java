package com.zeabay.pulse.auth.infrastructure.adapter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.zeabay.common.api.exception.BusinessException;
import com.zeabay.common.api.exception.ErrorCode;
import com.zeabay.common.keycloak.client.ZeabayKeycloakClient;
import com.zeabay.common.keycloak.dto.KeycloakTokenResponse;
import com.zeabay.pulse.auth.application.dto.LoginCommand;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

@ExtendWith(MockitoExtension.class)
class KeycloakAdapterTest {

  @Mock ZeabayKeycloakClient keycloakClient;
  @InjectMocks KeycloakAdapter adapter;

  @Test
  void loginUser_success_mapsToAuthTokenResult() {
    when(keycloakClient.loginUser(any()))
        .thenReturn(Mono.just(new KeycloakTokenResponse("access.token", "refresh.token", 300)));

    StepVerifier.create(adapter.loginUser(new LoginCommand("user", "pass")))
        .assertNext(
            result -> {
              assertThat(result.accessToken()).isEqualTo("access.token");
              assertThat(result.refreshToken()).isEqualTo("refresh.token");
              assertThat(result.expiresIn()).isEqualTo(300);
            })
        .verifyComplete();
  }

  @Test
  void loginUser_keycloakReturnsError_throwsUnauthorized() {
    when(keycloakClient.loginUser(any()))
        .thenReturn(
            Mono.error(WebClientResponseException.create(401, "Unauthorized", null, null, null)));

    StepVerifier.create(adapter.loginUser(new LoginCommand("user", "wrong")))
        .expectErrorSatisfies(
            ex -> {
              assertThat(ex).isInstanceOf(BusinessException.class);
              assertThat(((BusinessException) ex).getErrorCode()).isEqualTo(ErrorCode.UNAUTHORIZED);
              assertThat(ex.getMessage()).isEqualTo("Invalid credentials");
            })
        .verify();
  }

  @Test
  void refreshToken_keycloakReturnsError_throwsUnauthorized() {
    when(keycloakClient.refreshToken(any()))
        .thenReturn(
            Mono.error(WebClientResponseException.create(400, "Bad Request", null, null, null)));

    StepVerifier.create(adapter.refreshToken("expired.refresh.token"))
        .expectErrorSatisfies(
            ex -> {
              assertThat(ex).isInstanceOf(BusinessException.class);
              assertThat(((BusinessException) ex).getErrorCode()).isEqualTo(ErrorCode.UNAUTHORIZED);
              assertThat(ex.getMessage()).contains("expired");
            })
        .verify();
  }
}
