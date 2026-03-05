package com.zeabay.pulse.auth.api.mapper;

import com.zeabay.pulse.auth.api.dto.AuthTokenApiResponse;
import com.zeabay.pulse.auth.api.dto.LoginApiRequest;
import com.zeabay.pulse.auth.api.dto.RegisterApiRequest;
import com.zeabay.pulse.auth.api.dto.RegisterApiResponse;
import com.zeabay.pulse.auth.application.dto.AuthTokenResult;
import com.zeabay.pulse.auth.application.dto.LoginCommand;
import com.zeabay.pulse.auth.application.dto.RegisterUserCommand;
import com.zeabay.pulse.auth.domain.model.AuthUser;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface AuthMapper {

  RegisterUserCommand toRegisterCommand(RegisterApiRequest request);

  @Mapping(source = "username", target = "usernameOrEmail")
  LoginCommand toLoginCommand(LoginApiRequest request);

  AuthTokenApiResponse toTokenApiResponse(AuthTokenResult result);

  @Mapping(target = "id", expression = "java(String.valueOf(user.getId()))")
  RegisterApiResponse toRegisterApiResponse(AuthUser user);
}
