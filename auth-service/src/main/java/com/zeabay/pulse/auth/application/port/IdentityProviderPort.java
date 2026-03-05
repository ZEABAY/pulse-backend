package com.zeabay.pulse.auth.application.port;

import com.zeabay.pulse.auth.application.dto.AuthTokenResult;
import com.zeabay.pulse.auth.application.dto.LoginCommand;
import com.zeabay.pulse.auth.application.dto.RegisterUserCommand;
import reactor.core.publisher.Mono;

public interface IdentityProviderPort {

  Mono<String> registerUser(RegisterUserCommand command);

  Mono<AuthTokenResult> loginUser(LoginCommand command);

  Mono<Void> setEmailVerified(String keycloakId, boolean verified);

  Mono<AuthTokenResult> refreshToken(String refreshToken);

  Mono<Void> logout(String keycloakId);
}
