package com.zeabay.pulse.auth.infrastructure.adapter;

import com.zeabay.common.keycloak.client.ZeabayKeycloakClient;
import com.zeabay.common.keycloak.dto.KeycloakRegistrationRequest;
import com.zeabay.common.keycloak.dto.KeycloakTokenRequest;
import com.zeabay.common.logging.Loggable;
import com.zeabay.pulse.auth.application.dto.AuthTokenResult;
import com.zeabay.pulse.auth.application.dto.LoginCommand;
import com.zeabay.pulse.auth.application.dto.RegisterUserCommand;
import com.zeabay.pulse.auth.application.port.IdentityProviderPort;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

@Slf4j
@Loggable
@Component
@RequiredArgsConstructor
public class KeycloakAdapter implements IdentityProviderPort {

  private final ZeabayKeycloakClient zeabayKeycloakClient;

  @Override
  public Mono<String> registerUser(RegisterUserCommand command) {
    KeycloakRegistrationRequest request =
        KeycloakRegistrationRequest.builder()
            .username(command.username())
            .email(command.email())
            .password(command.password())
            .build();

    return zeabayKeycloakClient.registerUser(request);
  }

  @Override
  public Mono<AuthTokenResult> loginUser(LoginCommand command) {
    KeycloakTokenRequest request =
        KeycloakTokenRequest.builder()
            .usernameOrEmail(command.usernameOrEmail())
            .password(command.password())
            .build();

    return zeabayKeycloakClient
        .loginUser(request)
        .map(
            response ->
                new AuthTokenResult(
                    response.accessToken(), response.refreshToken(), response.expiresIn()));
  }
}
