package com.zeabay.pulse.hello.api.dto;

import lombok.Builder;

@Builder
public record CallSelfResponse(String traceId, String downstreamTraceId, String downstreamBody) {}
