package com.zeabay.pulse.profile.application.port.out;

import com.zeabay.pulse.profile.domain.model.UserProfile;
import reactor.core.publisher.Mono;

/**
 * Output port for profile caching operations.
 *
 * <p>Isolates the application layer from the concrete cache implementation. Implemented by {@link
 * com.zeabay.pulse.profile.infrastructure.adapter.RedisProfileCacheAdapter}.
 */
public interface ProfileCachePort {

  Mono<UserProfile> get(String key);

  Mono<Void> put(String key, UserProfile profile);

  Mono<Void> evict(String key);
}
