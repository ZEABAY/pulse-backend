package com.zeabay.pulse.auth.application.usecase;

import com.zeabay.pulse.auth.application.dto.AuthTokenResult;
import com.zeabay.pulse.auth.application.dto.LoginCommand;
import com.zeabay.pulse.auth.application.dto.RegisterUserCommand;
import com.zeabay.pulse.auth.domain.model.AuthUser;
import reactor.core.publisher.Mono;

public interface AuthService {

  Mono<AuthUser> registerUser(RegisterUserCommand command);

  Mono<AuthTokenResult> loginUser(LoginCommand command);
}
