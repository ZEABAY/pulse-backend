package com.zeabay.pulse.auth.config;

import com.zeabay.pulse.auth.application.port.TokenBlacklistPort;
import lombok.RequiredArgsConstructor;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

/**
 * WebFilter that blocks requests carrying a blacklisted JWT.
 *
 * <p>Runs at order {@code 0}, after the Spring Security filter chain (order {@code -100}) has
 * already validated the JWT signature. When the token's {@code jti} claim is absent, the raw token
 * value is SHA-256 hashed via {@link JwtHashUtils#sha256} to derive a stable blacklist key.
 *
 * <p>Returns {@code 401 Unauthorized} and short-circuits the filter chain if the jti is
 * blacklisted; otherwise, delegates normally.
 */
@Component
@Order(0)
@RequiredArgsConstructor
public class JwtBlacklistFilter implements WebFilter {

  private final TokenBlacklistPort tokenBlacklistPort;

  @Override
  public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
    return ReactiveSecurityContextHolder.getContext()
        .map(ctx -> ctx.getAuthentication())
        .flatMap(
            auth -> {
              if (auth == null
                  || !auth.isAuthenticated()
                  || !(auth instanceof JwtAuthenticationToken jwtAuth)) {
                return chain.filter(exchange);
              }
              String jti = resolveJti(jwtAuth.getToken());
              return tokenBlacklistPort
                  .isBlacklisted(jti)
                  .flatMap(
                      blacklisted -> {
                        if (blacklisted) {
                          exchange.getResponse().setStatusCode(HttpStatus.UNAUTHORIZED);
                          return exchange.getResponse().setComplete();
                        }
                        return chain.filter(exchange);
                      });
            })
        .switchIfEmpty(chain.filter(exchange));
  }

  private static String resolveJti(Jwt jwt) {
    String jti = jwt.getClaimAsString("jti");
    return (jti != null && !jti.isBlank()) ? jti : JwtHashUtils.sha256(jwt.getTokenValue());
  }
}
