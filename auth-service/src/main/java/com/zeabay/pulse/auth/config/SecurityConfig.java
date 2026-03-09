package com.zeabay.pulse.auth.config;

import java.util.List;
import java.util.Map;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.method.configuration.EnableReactiveMethodSecurity;
import org.springframework.security.config.annotation.web.reactive.EnableWebFluxSecurity;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.server.resource.authentication.ReactiveJwtAuthenticationConverter;
import org.springframework.security.web.server.SecurityWebFilterChain;
import reactor.core.publisher.Flux;

@Configuration
@EnableWebFluxSecurity
@EnableReactiveMethodSecurity
public class SecurityConfig {

  private static final String[] PUBLIC_PATHS = {
    "/api/v1/auth/register",
    "/api/v1/auth/login",
    "/api/v1/auth/verify",
    "/api/v1/auth/refresh",
    "/actuator/health",
    "/actuator/health/**",
    "/v3/api-docs/**",
    "/swagger-ui/**",
    "/swagger-ui.html",
    "/webjars/**"
  };

  @Bean
  public SecurityWebFilterChain securityWebFilterChain(ServerHttpSecurity http) {
    return http.csrf(ServerHttpSecurity.CsrfSpec::disable)
        .authorizeExchange(
            ex -> ex.pathMatchers(PUBLIC_PATHS).permitAll().anyExchange().authenticated())
        .oauth2ResourceServer(
            oauth2 ->
                oauth2.jwt(jwt -> jwt.jwtAuthenticationConverter(jwtAuthenticationConverter())))
        .build();
  }

  /**
   * Maps Keycloak's {@code realm_access.roles} claim to Spring Security {@link
   * SimpleGrantedAuthority} with the {@code ROLE_} prefix.
   *
   * <p>Example: Keycloak role {@code "user"} → authority {@code "ROLE_USER"}, enabling
   * {@code @PreAuthorize("hasRole('USER')")} across all services.
   */
  @Bean
  public ReactiveJwtAuthenticationConverter jwtAuthenticationConverter() {
    var converter = new ReactiveJwtAuthenticationConverter();
    converter.setJwtGrantedAuthoritiesConverter(
        jwt -> {
          Map<String, Object> realmAccess = jwt.getClaimAsMap("realm_access");
          if (realmAccess == null) {
            return Flux.empty();
          }
          @SuppressWarnings("unchecked")
          List<String> roles = (List<String>) realmAccess.getOrDefault("roles", List.of());
          return Flux.fromIterable(roles)
              .map(role -> new SimpleGrantedAuthority("ROLE_" + role.toUpperCase()));
        });
    return converter;
  }
}
