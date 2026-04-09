package com.zeabay.pulse.auth.api.mapper;

import com.zeabay.pulse.auth.api.dto.AuthTokenApiResponse;
import com.zeabay.pulse.auth.api.dto.LoginApiRequest;
import com.zeabay.pulse.auth.api.dto.RegisterApiRequest;
import com.zeabay.pulse.auth.application.dto.AuthTokenResult;
import com.zeabay.pulse.auth.application.dto.LoginCommand;
import com.zeabay.pulse.auth.application.dto.RegisterUserCommand;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface AuthMapper {

  RegisterUserCommand toRegisterCommand(RegisterApiRequest request);

  LoginCommand toLoginCommand(LoginApiRequest request);

  AuthTokenApiResponse toTokenApiResponse(AuthTokenResult result);
}
