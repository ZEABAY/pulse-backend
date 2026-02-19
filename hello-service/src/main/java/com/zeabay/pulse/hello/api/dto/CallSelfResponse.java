package com.zeabay.pulse.hello.api.dto;

import com.zeabay.common.api.model.ApiResponse;

public record CallSelfResponse(
    String traceId, String downstreamTraceId, ApiResponse<String> downstreamBody) {}
