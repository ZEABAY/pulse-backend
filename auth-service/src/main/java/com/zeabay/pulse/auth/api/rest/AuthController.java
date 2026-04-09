package com.zeabay.pulse.auth.api.rest;

import com.zeabay.common.api.exception.BusinessException;
import com.zeabay.common.api.exception.ErrorCode;
import com.zeabay.common.api.model.ZeabayApiResponse;
import com.zeabay.common.logging.Loggable;
import com.zeabay.common.web.ZeabayResponses;
import com.zeabay.pulse.auth.api.dto.AuthTokenApiResponse;
import com.zeabay.pulse.auth.api.dto.ForgotPasswordApiRequest;
import com.zeabay.pulse.auth.api.dto.LoginApiRequest;
import com.zeabay.pulse.auth.api.dto.RefreshTokenApiRequest;
import com.zeabay.pulse.auth.api.dto.RegisterApiRequest;
import com.zeabay.pulse.auth.api.dto.ResetPasswordApiRequest;
import com.zeabay.pulse.auth.api.dto.VerifyEmailApiRequest;
import com.zeabay.pulse.auth.api.dto.VerifyResetOtpApiRequest;
import com.zeabay.pulse.auth.api.mapper.AuthMapper;
import com.zeabay.pulse.auth.application.usecase.AuthService;
import com.zeabay.pulse.auth.config.JwtHashUtils;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.time.Instant;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
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
          """
              Creates a new user in the system and Keycloak. Sends a verification email via the\
               outbox.

              **Username rules:**
              - 3-30 characters, letters/digits/underscores/periods only
              - Must not start or end with a period
              - Consecutive periods are not allowed

              **Password rules:**
              - 8-100 characters
              - At least one uppercase letter, one lowercase letter, one digit, and one\
               special character""")
  @ApiResponses({
    @ApiResponse(responseCode = "200", description = "User created successfully"),
    @ApiResponse(
        responseCode = "400",
        description = "Invalid request payload or user already exists")
  })
  @PostMapping("/register")
  public Mono<ZeabayApiResponse<String>> register(@Valid @RequestBody RegisterApiRequest request) {
    return authService
        .registerUser(authMapper.toRegisterCommand(request))
        .then(ZeabayResponses.ok("User registered successfully"));
  }

  @Operation(
      summary = "Login user",
      description =
          """
              Authenticates user against Keycloak and returns JWT tokens.

              **Password rules:**
              - 8-100 characters
              - At least one uppercase letter, one lowercase letter, one digit, and one\
               special character""")
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
    @ApiResponse(responseCode = "400", description = "Invalid or expired verification code")
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
  @SecurityRequirement(name = "bearerAuth")
  public Mono<Void> logout(@Parameter(hidden = true) Mono<Authentication> auth) {
    return auth.filter(Authentication::isAuthenticated)
        .switchIfEmpty(
            Mono.error(new BusinessException(ErrorCode.UNAUTHORIZED, "Not authenticated")))
        .cast(JwtAuthenticationToken.class)
        .flatMap(jwtAuth -> buildLogoutMono(jwtAuth.getToken(), jwtAuth.getName()));
  }

  private Mono<Void> buildLogoutMono(Jwt jwt, String keycloakId) {
    String jti = jwt.getClaimAsString("jti");
    String blacklistKey =
        (jti != null && !jti.isBlank()) ? jti : JwtHashUtils.sha256(jwt.getTokenValue());

    Instant expiresAt = Objects.requireNonNull(jwt.getExpiresAt(), "JWT exp claim must be present");

    long ttlSeconds = Math.max(1, expiresAt.getEpochSecond() - Instant.now().getEpochSecond());

    return authService.logout(keycloakId, blacklistKey, ttlSeconds);
  }

  @Operation(
      summary = "Forgot password",
      description =
          "Initiates a password reset flow by sending a token to the user's email if it exists.")
  @ApiResponses({
    @ApiResponse(
        responseCode = "200",
        description = "Password reset request processed successfully"),
    @ApiResponse(responseCode = "400", description = "Invalid email format")
  })
  @PostMapping("/forgot-password")
  public Mono<ZeabayApiResponse<String>> forgotPassword(
      @Valid @RequestBody ForgotPasswordApiRequest request) {
    return authService
        .forgotPassword(request.email())
        .then(ZeabayResponses.ok("Password reset request processed"));
  }

  @Operation(
      summary = "Verify password reset OTP",
      description =
          "Validates the 6-digit OTP sent to the user's email before allowing password reset.")
  @ApiResponses({
    @ApiResponse(responseCode = "200", description = "OTP is valid"),
    @ApiResponse(responseCode = "400", description = "Invalid or expired OTP")
  })
  @PostMapping("/verify-reset-otp")
  public Mono<ZeabayApiResponse<String>> verifyResetOtp(
      @Valid @RequestBody VerifyResetOtpApiRequest request) {
    return authService
        .verifyResetOtp(request.email(), request.otp())
        .then(ZeabayResponses.ok("OTP is valid"));
  }

  @Operation(
      summary = "Reset password",
      description = "Resets user password using the OTP verified previously.")
  @ApiResponses({
    @ApiResponse(responseCode = "200", description = "Password reset successfully"),
    @ApiResponse(
        responseCode = "400",
        description = "Invalid or expired OTP, or password validation failed")
  })
  @PostMapping("/reset-password")
  public Mono<ZeabayApiResponse<String>> resetPassword(
      @Valid @RequestBody ResetPasswordApiRequest request) {
    return authService
        .resetPassword(request.email(), request.otp(), request.password())
        .then(ZeabayResponses.ok("Password reset successfully"));
  }
}
