package com.zeabay.pulse.auth.infrastructure.adapter;

import com.zeabay.common.logging.Loggable;
import com.zeabay.common.redis.ZeabayRedisProperties;
import com.zeabay.pulse.auth.application.port.TokenBlacklistPort;
import java.time.Duration;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.ReactiveRedisTemplate;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

/**
 * Redis-backed implementation of {@link TokenBlacklistPort}.
 *
 * <p>Stores blacklisted JTI keys with a TTL matching the token's remaining lifetime, so expired
 * entries are automatically evicted.
 */
@Loggable
@Component
@RequiredArgsConstructor
public class RedisTokenBlacklistAdapter implements TokenBlacklistPort {

  private static final String BLACKLIST_KEY_TEMPLATE = "%s:blacklist:%s";

  private final ReactiveRedisTemplate<String, String> zeabayReactiveRedisTemplate;
  private final ZeabayRedisProperties redisProperties;

  @Override
  public Mono<Void> add(String jti, long ttlSeconds) {
    String key = blacklistKey(jti);
    String value = java.time.Instant.now().toString();
    Duration ttl = Duration.ofSeconds(Math.max(1, ttlSeconds));
    return zeabayReactiveRedisTemplate.opsForValue().set(key, value, ttl).then();
  }

  @Override
  public Mono<Boolean> isBlacklisted(String jti) {
    String key = blacklistKey(jti);
    return zeabayReactiveRedisTemplate.hasKey(key);
  }

  private String blacklistKey(String jti) {
    return String.format(BLACKLIST_KEY_TEMPLATE, redisProperties.getPrefix(), jti);
  }
}
