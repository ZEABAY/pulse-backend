package com.zeabay.pulse.profile.domain.repository;

import com.zeabay.pulse.profile.domain.model.UserProfile;
import org.springframework.data.r2dbc.repository.R2dbcRepository;
import reactor.core.publisher.Mono;

/** R2DBC repository for {@link UserProfile} persistence operations. */
public interface UserProfileRepository extends R2dbcRepository<UserProfile, Long> {

  Mono<UserProfile> findByKeycloakId(String keycloakId);

  Mono<UserProfile> findByUsername(String username);
}
