package com.zeabay.pulse.gateway;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * pulse-gateway entry point.
 *
 * <p>All route definitions are in {@code application.yml}. Infrastructure beans (rate-limiter key
 * resolver, CORS, trace-id propagation, global error handling) are provided by {@link
 * GatewayConfig} and zeabay-common auto-configurations ({@code zeabay-webflux}, {@code
 * zeabay-redis}).
 */
@SpringBootApplication
public class GatewayApplication {

  public static void main(String[] args) {
    SpringApplication.run(GatewayApplication.class, args);
  }
}
