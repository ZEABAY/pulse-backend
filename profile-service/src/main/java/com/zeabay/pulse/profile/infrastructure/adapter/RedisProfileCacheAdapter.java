package com.zeabay.pulse.profile.infrastructure.adapter;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.zeabay.common.logging.Loggable;
import com.zeabay.pulse.profile.application.port.out.ProfileCachePort;
import com.zeabay.pulse.profile.domain.model.UserProfile;
import java.time.Duration;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

/**
 * Infrastructure adapter that implements {@link ProfileCachePort} using Redis.
 *
 * <p>Profiles are serialized as JSON strings and cached with a 15-minute TTL. Cache keys follow the
 * pattern {@code zeabay:profile:{key}}.
 */
@Slf4j
@Loggable(logResult = false)
@Component
@RequiredArgsConstructor
public class RedisProfileCacheAdapter implements ProfileCachePort {

  private static final String PREFIX = "zeabay:profile:";
  private static final Duration TTL = Duration.ofMinutes(15);

  private final ReactiveStringRedisTemplate redisTemplate;
  private final ObjectMapper objectMapper;

  @Override
  public Mono<UserProfile> get(String key) {
    return redisTemplate
        .opsForValue()
        .get(PREFIX + key)
        .flatMap(
            json -> {
              try {
                return Mono.just(objectMapper.readValue(json, UserProfile.class));
              } catch (Exception e) {
                log.warn(
                    "Failed to deserialize cached profile for key={}: {}", key, e.getMessage());
                return Mono.empty();
              }
            });
  }

  @Override
  public Mono<Void> put(String key, UserProfile profile) {
    try {
      String json = objectMapper.writeValueAsString(profile);
      return redisTemplate.opsForValue().set(PREFIX + key, json, TTL).then();
    } catch (Exception e) {
      log.warn("Failed to serialize profile for caching key={}: {}", key, e.getMessage());
      return Mono.empty();
    }
  }

  @Override
  public Mono<Void> evict(String key) {
    return redisTemplate.delete(PREFIX + key).then();
  }
}
