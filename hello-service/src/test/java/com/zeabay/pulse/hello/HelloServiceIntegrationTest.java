package com.zeabay.pulse.hello;

import com.zeabay.common.autoconfigure.ZeabayCommonAutoConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.web.reactive.server.WebTestClient;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class HelloServiceIntegrationTest {

  @LocalServerPort int port;

  @Test
  void traceparent_is_supported_and_has_precedence_over_x_trace_id() {
    String traceparent = "00-4BF92F3577B34DA6A3CE929D0E0E4736-00F067AA0BA902B7-01";
    String expectedTraceId = "4bf92f3577b34da6a3ce929d0e0e4736";

    String xTraceId = "abc-123";

    WebTestClient webTestClient =
        WebTestClient.bindToServer().baseUrl("http://localhost:" + port).build();

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
  void actuator_probes_are_up() {
    WebTestClient webTestClient =
        WebTestClient.bindToServer().baseUrl("http://localhost:" + port).build();

    webTestClient.get().uri("/actuator/health").exchange().expectStatus().isOk();
    webTestClient.get().uri("/actuator/health/liveness").exchange().expectStatus().isOk();
    webTestClient.get().uri("/actuator/health/readiness").exchange().expectStatus().isOk();
  }
}
