package com.zeabay.pulse.hello;

import com.zeabay.common.autoconfigure.ZeabayCommonAutoConfiguration;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.net.URI;

@Slf4j
@RestController
@RequiredArgsConstructor
public class HelloController {

    private final WebClient webClient;

    @GetMapping("/ping")
    public Mono<String> ping() {
        log.info("ping called");
        return Mono.just("pong");
    }

    @GetMapping("/call-self")
    public Mono<CallSelfResponse> callSelf(ServerHttpRequest request) {
        URI pingUri = buildPingUri(request);

        return Mono.deferContextual(ctx -> {
            String traceId = ctx.getOrDefault(ZeabayCommonAutoConfiguration.TRACE_ID_CTX_KEY, "missing");

            return webClient.get()
                    .uri(pingUri)
                    .retrieve()
                    .toEntity(String.class)
                    .map(entity -> new CallSelfResponse(
                            traceId,
                            entity.getHeaders().getFirst(ZeabayCommonAutoConfiguration.TRACE_ID_HEADER),
                            entity.getBody()
                    ));
        });
    }

    private static URI buildPingUri(ServerHttpRequest request) {
        URI in = request.getURI();
        String base = in.getScheme() + "://" + in.getAuthority();
        return URI.create(base + "/ping");
    }

    public record CallSelfResponse(String traceId, String downstreamTraceId, String body) {}
}
