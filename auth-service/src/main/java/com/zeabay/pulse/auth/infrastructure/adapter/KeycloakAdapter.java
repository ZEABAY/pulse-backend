package com.zeabay.pulse.auth.infrastructure.adapter;

import com.zeabay.common.api.exception.BusinessException;
import com.zeabay.common.api.exception.ErrorCode;
import com.zeabay.common.keycloak.client.ZeabayKeycloakClient;
import com.zeabay.common.keycloak.dto.KeycloakRegistrationRequest;
import com.zeabay.common.keycloak.dto.KeycloakTokenRequest;
import com.zeabay.common.keycloak.dto.ZeabayTokenResponse;
import com.zeabay.common.logging.Loggable;
import com.zeabay.pulse.auth.application.dto.AuthTokenResult;
import com.zeabay.pulse.auth.application.dto.LoginCommand;
import com.zeabay.pulse.auth.application.dto.RegisterUserCommand;
import com.zeabay.pulse.auth.application.port.IdentityProviderPort;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.core.publisher.Mono;

@Loggable
@Component
@RequiredArgsConstructor
public class KeycloakAdapter implements IdentityProviderPort {

  private final ZeabayKeycloakClient zeabayKeycloakClient;

  private static AuthTokenResult toAuthTokenResult(ZeabayTokenResponse r) {
    return new AuthTokenResult(
        r.accessToken(), r.refreshToken(), r.expiresIn(), r.refreshExpiresIn());
  }

  @Override
  public Mono<String> registerUser(RegisterUserCommand command) {
    return zeabayKeycloakClient.registerUser(
        KeycloakRegistrationRequest.builder()
            .username(command.username())
            .email(command.email())
            .password(command.password())
            .build());
  }

  @Override
  public Mono<Void> assignRole(String keycloakId, String roleName) {
    return zeabayKeycloakClient.assignRealmRole(keycloakId, roleName);
  }

  @Override
  public Mono<AuthTokenResult> loginUser(LoginCommand command) {
    return zeabayKeycloakClient
        .loginUser(
            KeycloakTokenRequest.builder()
                .usernameOrEmail(command.usernameOrEmail())
                .password(command.password())
                .build())
        .map(KeycloakAdapter::toAuthTokenResult)
        .onErrorMap(
            WebClientResponseException.class,
            ex ->
                new BusinessException(
                    ErrorCode.UNAUTHORIZED,
                    "Keycloak login failed for "
                        + command.usernameOrEmail()
                        + ": "
                        + ex.getStatusCode()
                        + " - "
                        + ex.getResponseBodyAsString()));
  }

  @Override
  public Mono<Void> setEmailVerified(String keycloakId, boolean verified) {
    return zeabayKeycloakClient.setEmailVerified(keycloakId, verified);
  }

  @Override
  public Mono<AuthTokenResult> refreshToken(String refreshToken) {
    return zeabayKeycloakClient
        .refreshToken(refreshToken)
        .map(KeycloakAdapter::toAuthTokenResult)
        .onErrorMap(
            WebClientResponseException.class,
            _ -> new BusinessException(ErrorCode.UNAUTHORIZED, "Invalid or expired refresh token"));
  }

  @Override
  public Mono<Void> logout(String keycloakId) {
    return zeabayKeycloakClient.logout(keycloakId);
  }

  @Override
  public Mono<Void> deleteUser(String keycloakId) {
    return zeabayKeycloakClient.deleteUser(keycloakId);
  }

  @Override
  public Mono<Void> resetPassword(String keycloakId, String newPassword) {
    return zeabayKeycloakClient.resetPassword(keycloakId, newPassword);
  }
}
