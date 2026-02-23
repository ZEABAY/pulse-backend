package com.zeabay.pulse.hello.api.dto;

import java.util.List;
import lombok.Builder;

@Builder
public record HeadersResponse(List<HeaderItem> headers) {}
