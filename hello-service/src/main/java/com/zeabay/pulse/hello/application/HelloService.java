package com.zeabay.pulse.hello.application;

import com.zeabay.common.api.model.ApiResponse;
import com.zeabay.pulse.hello.api.dto.CallSelfResponse;
import com.zeabay.pulse.hello.api.dto.HeadersResponse;
import com.zeabay.pulse.hello.api.dto.HealthResponse;
import com.zeabay.pulse.hello.api.dto.TimeResponse;
import com.zeabay.pulse.hello.api.dto.TraceResponse;
import com.zeabay.pulse.hello.api.dto.ValidationDemoResponse;
import java.net.URI;
import java.util.List;
import java.util.Map;
import reactor.core.publisher.Mono;

public interface HelloService {
  Mono<ApiResponse<String>> ping();

  Mono<ApiResponse<String>> hello();

  Mono<ApiResponse<HealthResponse>> health();

  Mono<ApiResponse<TimeResponse>> time();

  Mono<ApiResponse<TraceResponse>> trace();

  Mono<ApiResponse<HeadersResponse>> headers(Map<String, List<String>> headersMap);

  Mono<ApiResponse<ValidationDemoResponse>> validateDemo(String q);

  Mono<ApiResponse<CallSelfResponse>> callSelf(URI pingUri);
}
