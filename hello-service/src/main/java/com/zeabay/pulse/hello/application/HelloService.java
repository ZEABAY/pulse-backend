package com.zeabay.pulse.hello.application;

import com.zeabay.common.api.exception.ErrorCode;
import com.zeabay.common.api.model.ApiResponse;
import com.zeabay.common.api.model.ValidationError;
import com.zeabay.common.autoconfigure.ZeabayCommonAutoConfiguration;
import com.zeabay.common.web.ZeabayResponses;
import com.zeabay.pulse.hello.api.dto.CallSelfResponse;
import com.zeabay.pulse.hello.api.dto.HeaderItem;
import com.zeabay.pulse.hello.api.dto.HeadersResponse;
import com.zeabay.pulse.hello.api.dto.HealthResponse;
import com.zeabay.pulse.hello.api.dto.TimeResponse;
import com.zeabay.pulse.hello.api.dto.TraceResponse;
import com.zeabay.pulse.hello.api.dto.ValidationDemoResponse;
import java.net.URI;
import java.time.Instant;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.health.actuate.endpoint.HealthEndpoint;
import org.springframework.boot.health.contributor.Status;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

@Slf4j
@Service
@RequiredArgsConstructor
public class HelloService implements HelloUseCase {

  private static final ParameterizedTypeReference<ApiResponse<String>> API_STRING =
      new ParameterizedTypeReference<>() {};

  private final WebClient webClient;
  private final HealthEndpoint healthEndpoint;

  private static URI buildPingUri(ServerHttpRequest request) {
    URI in = request.getURI();
    String base = in.getScheme() + "://" + in.getAuthority();
    return URI.create(base + "/ping");
  }

  @Override
  public Mono<ApiResponse<String>> ping() {
    log.info("ping called");
    return ZeabayResponses.ok("pong");
  }

  @Override
  public Mono<ApiResponse<String>> hello() {
    return ZeabayResponses.ok("hello world");
  }

  @Override
  public Mono<ApiResponse<HealthResponse>> health() {
    return Mono.fromSupplier(healthEndpoint::health)
        .flatMap(
            component -> {
              Status status = component.getStatus();
              return ZeabayResponses.ok(new HealthResponse(status.getCode()));
            });
  }

  @Override
  public Mono<ApiResponse<TimeResponse>> time() {
    return ZeabayResponses.ok(new TimeResponse(Instant.now().toString()));
  }

  @Override
  public Mono<ApiResponse<TraceResponse>> trace() {
    return Mono.deferContextual(
        ctx ->
            ZeabayResponses.ok(
                new TraceResponse(
                    ZeabayResponses.traceId(ctx), ZeabayCommonAutoConfiguration.TRACE_ID_CTX_KEY)));
  }

  @Override
  public Mono<ApiResponse<HeadersResponse>> headers(ServerHttpRequest request) {
    List<HeaderItem> items = new java.util.ArrayList<>();

    request
        .getHeaders()
        .forEach((name, values) -> items.add(new HeaderItem(name, List.copyOf(values))));

    items.sort(java.util.Comparator.comparing(HeaderItem::name));
    return ZeabayResponses.ok(new HeadersResponse(List.copyOf(items)));
  }

  @Override
  public Mono<ApiResponse<ValidationDemoResponse>> validateDemo(
      ServerHttpRequest request, String q) {

    if (q == null || q.isBlank()) {
      return ZeabayResponses.fail(
          request,
          ErrorCode.VALIDATION_ERROR,
          "Query param 'q' is required",
          List.of(new ValidationError("q", "must not be blank")));
    }

    return ZeabayResponses.ok(new ValidationDemoResponse(q, q.length()));
  }

  @Override
  public Mono<ApiResponse<CallSelfResponse>> callSelf(ServerHttpRequest request) {
    URI pingUri = buildPingUri(request);

    return Mono.deferContextual(
        ctx -> {
          String upstreamTraceId = ZeabayResponses.traceId(ctx);

          return webClient
              .get()
              .uri(pingUri)
              .retrieve()
              .toEntity(API_STRING)
              .map(
                  entity -> {
                    String downstreamTraceId =
                        entity.getHeaders().getFirst(ZeabayCommonAutoConfiguration.TRACE_ID_HEADER);

                    ApiResponse<String> downstreamBody = entity.getBody();
                    return new CallSelfResponse(upstreamTraceId, downstreamTraceId, downstreamBody);
                  })
              .map(body -> ApiResponse.ok(body, upstreamTraceId));
        });
  }
}
