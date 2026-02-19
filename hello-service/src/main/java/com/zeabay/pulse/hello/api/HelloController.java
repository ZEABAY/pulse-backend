package com.zeabay.pulse.hello.api;

import com.zeabay.common.api.model.ApiResponse;
import com.zeabay.pulse.hello.api.dto.CallSelfResponse;
import com.zeabay.pulse.hello.api.dto.HeadersResponse;
import com.zeabay.pulse.hello.api.dto.HealthResponse;
import com.zeabay.pulse.hello.api.dto.TimeResponse;
import com.zeabay.pulse.hello.api.dto.TraceResponse;
import com.zeabay.pulse.hello.api.dto.ValidationDemoResponse;
import com.zeabay.pulse.hello.application.HelloUseCase;
import lombok.RequiredArgsConstructor;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

@RestController
@RequiredArgsConstructor
public class HelloController {

  private final HelloUseCase helloUseCase;

  @GetMapping("/ping")
  public Mono<ApiResponse<String>> ping() {
    return helloUseCase.ping();
  }

  @GetMapping("/hello")
  public Mono<ApiResponse<String>> hello() {
    return helloUseCase.hello();
  }

  @GetMapping("/health")
  public Mono<ApiResponse<HealthResponse>> health() {
    return helloUseCase.health();
  }

  @GetMapping("/time")
  public Mono<ApiResponse<TimeResponse>> time() {
    return helloUseCase.time();
  }

  @GetMapping("/trace")
  public Mono<ApiResponse<TraceResponse>> trace() {
    return helloUseCase.trace();
  }

  @GetMapping("/headers")
  public Mono<ApiResponse<HeadersResponse>> headers(ServerHttpRequest request) {
    return helloUseCase.headers(request);
  }

  @GetMapping("/validate-demo")
  public Mono<ApiResponse<ValidationDemoResponse>> validateDemo(
      ServerHttpRequest request, @RequestParam(name = "q", required = false) String q) {
    return helloUseCase.validateDemo(request, q);
  }

  @GetMapping("/call-self")
  public Mono<ApiResponse<CallSelfResponse>> callSelf(ServerHttpRequest request) {
    return helloUseCase.callSelf(request);
  }
}
