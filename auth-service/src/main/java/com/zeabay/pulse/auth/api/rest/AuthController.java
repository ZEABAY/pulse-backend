package com.zeabay.pulse.auth.api.rest;

import com.zeabay.common.api.model.ZeabayApiResponse;
import com.zeabay.common.logging.Loggable;
import com.zeabay.common.web.ZeabayResponses;
import com.zeabay.pulse.auth.api.dto.AuthTokenApiResponse;
import com.zeabay.pulse.auth.api.dto.LoginApiRequest;
import com.zeabay.pulse.auth.api.dto.RefreshTokenApiRequest;
import com.zeabay.pulse.auth.api.dto.RegisterApiRequest;
import com.zeabay.pulse.auth.api.dto.RegisterApiResponse;
import com.zeabay.pulse.auth.api.dto.VerifyEmailApiRequest;
import com.zeabay.pulse.auth.api.mapper.AuthMapper;
import com.zeabay.pulse.auth.application.usecase.AuthService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.net.URI;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

@Loggable
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/auth")
@Tag(
    name = "Authentication",
    description = "Endpoints for user registration, login and token management")
public class AuthController {

  private final AuthService authService;
  private final AuthMapper authMapper;

  @Operation(
      summary = "Register a new user",
      description =
          "Creates a new user in the system and Keycloak. Sends a verification email via the outbox.")
  @ApiResponses({
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
  @ApiResponses({
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

  @Operation(
      summary = "Verify email address",
      description = "Validates the token sent by mail and activates the user account in Keycloak.")
  @ApiResponses({
    @ApiResponse(responseCode = "200", description = "Email verified successfully"),
    @ApiResponse(responseCode = "400", description = "Token expired or already used"),
    @ApiResponse(responseCode = "404", description = "Invalid token")
  })
  @PostMapping("/verify")
  public Mono<ZeabayApiResponse<String>> verifyEmail(
      @Valid @RequestBody VerifyEmailApiRequest request) {
    return authService
        .verifyEmail(request.email(), request.token())
        .then(ZeabayResponses.ok("Email verified successfully"));
  }

  @Operation(
      summary = "Refresh access token",
      description = "Exchanges a valid refresh token for a new access/refresh token pair.")
  @ApiResponses({
    @ApiResponse(responseCode = "200", description = "Tokens refreshed successfully"),
    @ApiResponse(responseCode = "401", description = "Invalid or expired refresh token")
  })
  @PostMapping("/refresh")
  public Mono<ZeabayApiResponse<AuthTokenApiResponse>> refresh(
      @Valid @RequestBody RefreshTokenApiRequest request) {
    return authService
        .refreshToken(request.refreshToken())
        .map(authMapper::toTokenApiResponse)
        .flatMap(ZeabayResponses::ok);
  }

  @Operation(
      summary = "Logout user",
      description = "Invalidates all Keycloak sessions for the authenticated user.",
      security = @SecurityRequirement(name = "bearerAuth"))
  @ApiResponses({
    @ApiResponse(responseCode = "204", description = "Logout successful"),
    @ApiResponse(responseCode = "401", description = "Not authenticated")
  })
  @PostMapping("/logout")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public Mono<Void> logout(Mono<Authentication> auth) {
    return auth.flatMap(authentication -> authService.logout(authentication.getName()));
  }
}
