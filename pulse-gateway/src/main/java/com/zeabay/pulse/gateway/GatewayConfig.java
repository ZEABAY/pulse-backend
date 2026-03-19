package com.zeabay.pulse.gateway;

import com.zeabay.common.logging.Loggable;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.gateway.filter.ratelimit.KeyResolver;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import reactor.core.publisher.Mono;

/**
 * Gateway-specific Spring configuration.
 *
 * <p>Provides beans required by Spring Cloud Gateway that are not covered by zeabay-common
 * auto-configuration (e.g., the rate-limiter key strategy).
 */
@Slf4j
@Configuration
public class GatewayConfig {

  /**
   * Resolves the rate-limiter bucket key from the client's remote IP address.
   *
   * <p>Falls back to {@code "unknown"} when the remote address is unavailable (e.g., in tests or
   * behind certain load balancers).
   */
  @Bean
  @Loggable
  public KeyResolver ipKeyResolver() {
    return exchange ->
        Mono.just(
            exchange.getRequest().getRemoteAddress() != null
                ? exchange.getRequest().getRemoteAddress().getAddress().getHostAddress()
                : "unknown");
  }
}
