package com.zeabay.pulse.auth.api.rest;

import com.zeabay.common.api.model.ZeabayApiResponse;
import com.zeabay.common.logging.Loggable;
import com.zeabay.common.web.ZeabayResponses;
import com.zeabay.pulse.auth.api.dto.AuthTokenApiResponse;
import com.zeabay.pulse.auth.api.dto.LoginApiRequest;
import com.zeabay.pulse.auth.api.dto.RegisterApiRequest;
import com.zeabay.pulse.auth.api.dto.RegisterApiResponse;
import com.zeabay.pulse.auth.api.mapper.AuthMapper;
import com.zeabay.pulse.auth.application.usecase.AuthService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.net.URI;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

@Loggable
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/auth")
@Tag(name = "Authentication", description = "Endpoints for user registration and login")
public class AuthController {

  private final AuthService authService;
  private final AuthMapper authMapper;

  @Operation(
      summary = "Register a new user",
      description = "Creates a new user in the system and Keycloak.")
  @ApiResponses(
      value = {
        @ApiResponse(responseCode = "201", description = "User created successfully"),
        @ApiResponse(
            responseCode = "400",
            description = "Invalid request payload or user already exists")
      })
  @PostMapping("/register")
  public Mono<ResponseEntity<ZeabayApiResponse<RegisterApiResponse>>> register(
      @Valid @RequestBody RegisterApiRequest request) {
    return authService
        .registerUser(authMapper.toRegisterCommand(request))
        .map(authMapper::toRegisterApiResponse)
        .flatMap(
            response ->
                ZeabayResponses.created(
                    response, URI.create("/api/v1/auth/users/" + response.id())));
  }

  @Operation(
      summary = "Login user",
      description = "Authenticates user against Keycloak and returns JWT tokens.")
  @ApiResponses(
      value = {
        @ApiResponse(responseCode = "200", description = "Successful authentication"),
        @ApiResponse(responseCode = "401", description = "Invalid credentials")
      })
  @PostMapping("/login")
  public Mono<ZeabayApiResponse<AuthTokenApiResponse>> login(
      @Valid @RequestBody LoginApiRequest request) {
    return authService
        .loginUser(authMapper.toLoginCommand(request))
        .map(authMapper::toTokenApiResponse)
        .flatMap(ZeabayResponses::ok);
  }
}
