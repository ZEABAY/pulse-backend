package com.zeabay.pulse.hello.application;

import com.zeabay.common.api.exception.ErrorCode;
import com.zeabay.common.api.model.ApiResponse;
import com.zeabay.common.api.model.ErrorResponse;
import com.zeabay.common.api.model.ValidationError;
import com.zeabay.common.constant.ZeabayConstants;
import com.zeabay.common.logging.Loggable;
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
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.health.actuate.endpoint.HealthEndpoint;
import org.springframework.boot.health.contributor.Status;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

@Slf4j
@Service
@Loggable
@RequiredArgsConstructor
public class HelloServiceImpl implements HelloService {

  private static final ParameterizedTypeReference<ApiResponse<String>> API_STRING =
      new ParameterizedTypeReference<>() {};

  private final WebClient webClient;
  private final HealthEndpoint healthEndpoint;

  @Override
  public Mono<ApiResponse<String>> ping() {
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
              HealthResponse response = HealthResponse.builder().status(status.toString()).build();
              return ZeabayResponses.ok(response);
            });
  }

  @Override
  public Mono<ApiResponse<TimeResponse>> time() {
    String now = Instant.now().toString();
    TimeResponse response = TimeResponse.builder().now(now).build();
    return ZeabayResponses.ok(response);
  }

  @Override
  public Mono<ApiResponse<TraceResponse>> trace() {
    return Mono.deferContextual(
        ctx -> {
          TraceResponse resp =
              TraceResponse.builder()
                  .traceId(ZeabayResponses.traceId(ctx))
                  .ctxKey(ZeabayConstants.TRACE_ID_CTX_KEY)
                  .build();

          return ZeabayResponses.ok(resp);
        });
  }

  @Override
  public Mono<ApiResponse<HeadersResponse>> headers(Map<String, List<String>> headersMap) {
    List<HeaderItem> headers = new ArrayList<>();

    headersMap.forEach((name, values) -> headers.add(new HeaderItem(name, List.copyOf(values))));

    headers.sort(Comparator.comparing(HeaderItem::name));
    HeadersResponse response = HeadersResponse.builder().headers(headers).build();
    return ZeabayResponses.ok(response);
  }

  @Override
  public Mono<ApiResponse<ValidationDemoResponse>> validateDemo(String q) {

    if (q == null || q.isBlank()) {
      return Mono.deferContextual(
          ctx -> {
            Instant now = Instant.now();

            ValidationError validationError =
                ValidationError.builder().field("q").message("must not be blank").build();

            ErrorResponse error =
                ErrorResponse.builder()
                    .code(ErrorCode.VALIDATION_ERROR.name())
                    .message("Query param 'q' is required")
                    .path(null)
                    .timestamp(now)
                    .validationErrors(List.of(validationError))
                    .build();

            ApiResponse<ValidationDemoResponse> response =
                ApiResponse.fail(error, ZeabayResponses.traceId(ctx));

            return Mono.just(response);
          });
    }

    ValidationDemoResponse data = ValidationDemoResponse.builder().q(q).length(q.length()).build();

    return ZeabayResponses.ok(data);
  }

  @Override
  public Mono<ApiResponse<CallSelfResponse>> callSelf(URI pingUri) {

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
                        entity.getHeaders().getFirst(ZeabayConstants.TRACE_ID_HEADER);

                    ApiResponse<String> downstreamBody = entity.getBody();
                    String extractedData = downstreamBody != null ? downstreamBody.data() : null;
                    return new CallSelfResponse(upstreamTraceId, downstreamTraceId, extractedData);
                  })
              .map(body -> ApiResponse.ok(body, upstreamTraceId));
        });
  }
}
