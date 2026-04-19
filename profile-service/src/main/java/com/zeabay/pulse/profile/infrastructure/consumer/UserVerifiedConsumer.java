package com.zeabay.pulse.profile.infrastructure.consumer;

import com.zeabay.common.inbox.BaseConsumer;
import com.zeabay.common.logging.Loggable;
import com.zeabay.pulse.profile.domain.model.UserProfile;
import com.zeabay.pulse.profile.domain.repository.UserProfileRepository;
import com.zeabay.pulse.shared.events.auth.UserVerifiedEvent;
import com.zeabay.pulse.shared.kafka.PulseTopics;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

/**
 * Kafka consumer that creates a skeleton user profile when a user verifies their email.
 *
 * <p>Listens to {@link PulseTopics#USER_VERIFIED} events published by {@code auth-service}. Creates
 * an empty profile with {@code profileCompleted = false}. The user will complete the profile via
 * the REST API.
 *
 * <p>Extends {@link BaseConsumer} for inbox idempotency — duplicate events are safely discarded.
 */
@Slf4j
@Loggable
@Component
public class UserVerifiedConsumer extends BaseConsumer<UserVerifiedEvent> {

  private final UserProfileRepository profileRepository;

  public UserVerifiedConsumer(UserProfileRepository profileRepository) {
    this.profileRepository = profileRepository;
  }

  @KafkaListener(
      topics = PulseTopics.USER_VERIFIED,
      groupId = PulseTopics.CONSUMER_GROUP_ID_PATTERN,
      containerFactory = "zeabayKafkaListenerContainerFactory")
  public void consume(UserVerifiedEvent event) {
    super.handleEvent(event);
  }

  @Override
  protected Mono<Void> doProcess(UserVerifiedEvent event) {
    log.info(
        "USER_VERIFIED_RECEIVED: userId={}, username={}, email={}",
        event.getUserId(),
        event.getUsername(),
        event.getEmail());

    return profileRepository
        .findByKeycloakId(event.getKeycloakId())
        .hasElement()
        .flatMap(
            exists -> {
              if (exists) {
                log.warn(
                    "Profile already exists for keycloakId={}, skipping", event.getKeycloakId());
                return Mono.empty();
              }
              UserProfile skeleton =
                  UserProfile.builder()
                      .keycloakId(event.getKeycloakId())
                      .username(event.getUsername())
                      .profileCompleted(false)
                      .build();
              return profileRepository
                  .save(skeleton)
                  .doOnSuccess(
                      saved ->
                          log.info(
                              "SKELETON_PROFILE_CREATED: keycloakId={}, username={}, profileId={}",
                              saved.getKeycloakId(),
                              saved.getUsername(),
                              saved.getId()))
                  .then();
            });
  }
}
