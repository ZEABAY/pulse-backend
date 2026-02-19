package com.zeabay.pulse.hello.application;

import com.zeabay.common.api.model.ApiResponse;
import com.zeabay.pulse.hello.api.dto.CallSelfResponse;
import com.zeabay.pulse.hello.api.dto.HeadersResponse;
import com.zeabay.pulse.hello.api.dto.HealthResponse;
import com.zeabay.pulse.hello.api.dto.TimeResponse;
import com.zeabay.pulse.hello.api.dto.TraceResponse;
import com.zeabay.pulse.hello.api.dto.ValidationDemoResponse;
import org.springframework.http.server.reactive.ServerHttpRequest;
import reactor.core.publisher.Mono;

public interface HelloUseCase {
  Mono<ApiResponse<String>> ping();

  Mono<ApiResponse<String>> hello();

  Mono<ApiResponse<HealthResponse>> health();

  Mono<ApiResponse<TimeResponse>> time();

  Mono<ApiResponse<TraceResponse>> trace();

  Mono<ApiResponse<HeadersResponse>> headers(ServerHttpRequest request);

  Mono<ApiResponse<ValidationDemoResponse>> validateDemo(ServerHttpRequest request, String q);

  Mono<ApiResponse<CallSelfResponse>> callSelf(ServerHttpRequest request);
}
