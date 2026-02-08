package com.zeabay.pulse.hello;

import static org.junit.jupiter.api.Assertions.assertTrue;

import com.zeabay.common.autoconfigure.ZeabayCommonAutoConfiguration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.MediaType;
import org.springframework.test.web.reactive.server.WebTestClient;

@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = {
      "management.info.env.enabled=true",
      "info.app.name=hello-service",
      "info.app.description=Pulse Sprint 0 hello-service",
      "info.app.version=1.0.0-SNAPSHOT"
    })
class HelloServiceIntegrationTest {

  @LocalServerPort private int port;

  private WebTestClient webTestClient;

  @BeforeEach
  void setUp() {
    this.webTestClient =
        WebTestClient.bindToServer()
            .baseUrl("http://localhost:" + port)
            .responseTimeout(java.time.Duration.ofSeconds(5))
            .build();
  }

  @Test
  void traceparent_is_supported_and_has_precedence_over_x_trace_id() {
    String traceparent = "00-4BF92F3577B34DA6A3CE929D0E0E4736-00F067AA0BA902B7-01";
    String expectedTraceId = "4bf92f3577b34da6a3ce929d0e0e4736";
    String xTraceId = "abc-123";

    webTestClient
        .get()
        .uri("/call-self")
        .header("traceparent", traceparent)
        .header(ZeabayCommonAutoConfiguration.TRACE_ID_HEADER, xTraceId)
        .exchange()
        .expectStatus()
        .isOk()
        .expectHeader()
        .valueEquals(ZeabayCommonAutoConfiguration.TRACE_ID_HEADER, expectedTraceId)
        .expectBody()
        .jsonPath("$.traceId")
        .isEqualTo(expectedTraceId)
        .jsonPath("$.downstreamTraceId")
        .isEqualTo(expectedTraceId)
        .jsonPath("$.body")
        .isEqualTo("pong");
  }

  @Test
  void actuator_probes_are_exposed_by_default() {
    webTestClient.get().uri("/actuator/health").exchange().expectStatus().isOk();
    webTestClient.get().uri("/actuator/health/liveness").exchange().expectStatus().isOk();
    webTestClient.get().uri("/actuator/health/readiness").exchange().expectStatus().isOk();
  }

  @Test
  void actuator_info_contains_app_metadata() {
    webTestClient
        .get()
        .uri("/actuator/info")
        .exchange()
        .expectStatus()
        .isOk()
        .expectBody()
        .jsonPath("$.app.name")
        .isEqualTo("hello-service")
        .jsonPath("$.app.description")
        .isEqualTo("Pulse Sprint 0 hello-service")
        .jsonPath("$.app.version")
        .isEqualTo("1.0.0-SNAPSHOT");
  }

  @Test
  void prometheus_endpoint_is_exposed_by_default() {
    webTestClient
        .get()
        .uri("/actuator/prometheus")
        .exchange()
        .expectStatus()
        .isOk()
        .expectHeader()
        .contentTypeCompatibleWith(MediaType.TEXT_PLAIN)
        .expectBody(String.class)
        .consumeWith(
            res -> {
              String body = res.getResponseBody();
              assertTrue(body != null && body.contains("# HELP"));
            });
  }

  @Test
  void actuator_info_contains_build_info_when_build_info_is_generated() {
    webTestClient
        .get()
        .uri("/actuator/info")
        .exchange()
        .expectStatus()
        .isOk()
        .expectBody()
        .jsonPath("$.build.version")
        .exists();
  }
}
