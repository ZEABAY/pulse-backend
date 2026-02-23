package com.zeabay.pulse.hello.api.dto;

import lombok.Builder;

@Builder
public record TraceResponse(String traceId, String ctxKey) {}
