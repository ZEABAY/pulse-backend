package com.zeabay.pulse.hello.api;

import com.zeabay.common.api.model.ApiResponse;
import com.zeabay.common.api.model.ErrorResponse;
import com.zeabay.common.logging.Loggable;
import com.zeabay.pulse.hello.api.dto.CallSelfResponse;
import com.zeabay.pulse.hello.api.dto.HeadersResponse;
import com.zeabay.pulse.hello.api.dto.HealthResponse;
import com.zeabay.pulse.hello.api.dto.TimeResponse;
import com.zeabay.pulse.hello.api.dto.TraceResponse;
import com.zeabay.pulse.hello.api.dto.ValidationDemoResponse;
import com.zeabay.pulse.hello.application.HelloService;
import java.net.URI;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

@Loggable
@RestController
@RequestMapping("/hello")
@RequiredArgsConstructor
public class HelloController {

  private final HelloService helloService;

  private static URI buildPingUri(ServerHttpRequest request) {
    URI uri = request.getURI();
    String base = uri.getScheme() + "://" + uri.getAuthority();
    return URI.create(base + "/hello/ping");
  }

  @GetMapping("/ping")
  public Mono<ApiResponse<String>> ping() {
    return helloService.ping();
  }

  @GetMapping("/hello")
  public Mono<ApiResponse<String>> hello() {
    return helloService.hello();
  }

  @GetMapping("/health")
  public Mono<ApiResponse<HealthResponse>> health() {
    return helloService.health();
  }

  @GetMapping("/time")
  public Mono<ApiResponse<TimeResponse>> time() {
    return helloService.time();
  }

  @GetMapping("/trace")
  public Mono<ApiResponse<TraceResponse>> trace() {
    return helloService.trace();
  }

  @GetMapping("/headers")
  public Mono<ApiResponse<HeadersResponse>> headers(ServerHttpRequest request) {
    Map<String, List<String>> headersMap = new HashMap<>();
    request.getHeaders().forEach((k, v) -> headersMap.put(k, List.copyOf(v)));
    return helloService.headers(headersMap);
  }

  @GetMapping("/validate-demo")
  public Mono<ApiResponse<ValidationDemoResponse>> validateDemo(
      ServerHttpRequest request, @RequestParam(name = "q", required = false) String q) {
    return helloService
        .validateDemo(q)
        .map(
            res -> {
              if (!res.success() && res.error() != null && res.error().path() == null) {
                var err =
                    new ErrorResponse(
                        res.error().code(),
                        res.error().message(),
                        request.getURI().getPath(),
                        res.error().timestamp() != null ? res.error().timestamp() : Instant.now(),
                        res.error().validationErrors());
                return new ApiResponse<>(false, null, err, res.traceId(), Instant.now());
              }
              return res;
            });
  }

  @GetMapping("/call-self")
  public Mono<ApiResponse<CallSelfResponse>> callSelf(ServerHttpRequest request) {
    return helloService.callSelf(buildPingUri(request));
  }
}
