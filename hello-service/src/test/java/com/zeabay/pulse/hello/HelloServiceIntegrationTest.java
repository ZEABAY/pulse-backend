package com.zeabay.pulse.hello;

import com.zeabay.common.autoconfigure.ZeabayCommonAutoConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.web.reactive.server.WebTestClient;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class HelloServiceIntegrationTest {

    @LocalServerPort
    int port;

    @Test
    void traceId_is_added_to_response_and_propagated_to_outbound_call() {
        String traceId = "abc-123";

        WebTestClient webTestClient = WebTestClient
                .bindToServer()
                .baseUrl("http://localhost:" + port)
                .build();

        webTestClient.get()
                .uri("/call-self")
                .header(ZeabayCommonAutoConfiguration.TRACE_ID_HEADER, traceId)
                .exchange()
                .expectStatus().isOk()
                .expectHeader().valueEquals(ZeabayCommonAutoConfiguration.TRACE_ID_HEADER, traceId)
                .expectBody()
                .jsonPath("$.traceId").isEqualTo(traceId)
                .jsonPath("$.downstreamTraceId").isEqualTo(traceId)
                .jsonPath("$.body").isEqualTo("pong");
    }
}
