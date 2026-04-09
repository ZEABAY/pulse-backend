package com.zeabay.pulse.auth.application.port;

import reactor.core.publisher.Mono;

/** Output port for JWT token blacklist operations (backed by Redis). */
public interface TokenBlacklistPort {

  Mono<Void> add(String jti, long ttlSeconds);

  Mono<Boolean> isBlacklisted(String jti);
}
