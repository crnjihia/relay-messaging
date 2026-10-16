package com.relay.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.relay.config.RedisConfig;
import com.relay.domain.PresenceInfo;
import com.relay.domain.UserStatus;
import com.relay.dto.PresenceEvent;
import com.relay.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.time.Instant;

@Service
public class PresenceService {

    private static final Logger log = LoggerFactory.getLogger(PresenceService.class);
    private static final String PRESENCE_KEY_PREFIX = "presence:";
    private static final Duration PRESENCE_TTL = Duration.ofSeconds(60);
    private static final Duration AWAY_GRACE_TTL = Duration.ofSeconds(60);

    private final ReactiveStringRedisTemplate redisTemplate;
    private final UserRepository userRepository;
    private final ObjectMapper objectMapper;

    public PresenceService(ReactiveStringRedisTemplate redisTemplate,
                           UserRepository userRepository,
                           ObjectMapper objectMapper) {
        this.redisTemplate = redisTemplate;
        this.userRepository = userRepository;
        this.objectMapper = objectMapper;
    }

    public Mono<Void> markOnline(String userId) {
        PresenceInfo presence = PresenceInfo.online(userId);
        return persistPresenceAndPublish(presence, PRESENCE_TTL);
    }

    public Mono<Void> recordHeartbeat(String userId) {
        PresenceInfo presence = PresenceInfo.online(userId);
        return persistPresenceAndPublish(presence, PRESENCE_TTL);
    }

    public Mono<Void> markAwayOnDisconnect(String userId) {
        // Explicit disconnect -> broadcast AWAY (grace) for 60s
        PresenceInfo presence = PresenceInfo.away(userId);
        return persistPresenceAndPublish(presence, AWAY_GRACE_TTL);
    }

    public Mono<Void> markOffline(String userId) {
        PresenceInfo presence = PresenceInfo.offline(userId);
        String key = PRESENCE_KEY_PREFIX + userId;

        return redisTemplate.delete(key)
                .then(publishPresenceUpdate(new PresenceEvent(userId, UserStatus.OFFLINE, Instant.now())))
                .then(updateUserStatusInDb(userId, UserStatus.OFFLINE, Instant.now()));
    }

    public Mono<UserStatus> getUserStatus(String userId) {
        String key = PRESENCE_KEY_PREFIX + userId;
        return redisTemplate.opsForValue().get(key)
                .flatMap(json -> {
                    try {
                        PresenceInfo info = objectMapper.readValue(json, PresenceInfo.class);
                        return Mono.just(info.status());
                    } catch (Exception e) {
                        return Mono.just(UserStatus.OFFLINE);
                    }
                })
                .defaultIfEmpty(UserStatus.OFFLINE);
    }

    public Mono<Void> handleExpiredKey(String expiredKey) {
        if (expiredKey != null && expiredKey.startsWith(PRESENCE_KEY_PREFIX)) {
            String userId = expiredKey.substring(PRESENCE_KEY_PREFIX.length());
            log.info("Redis presence TTL expired for userId: {}. Broadcasting OFFLINE status.", userId);
            return markOffline(userId);
        }
        return Mono.empty();
    }

    private Mono<Void> persistPresenceAndPublish(PresenceInfo presence, Duration ttl) {
        String key = PRESENCE_KEY_PREFIX + presence.userId();
        try {
            String json = objectMapper.writeValueAsString(presence);
            return redisTemplate.opsForValue().set(key, json, ttl)
                    .then(publishPresenceUpdate(new PresenceEvent(presence.userId(), presence.status(), presence.lastHeartbeat())))
                    .then(updateUserStatusInDb(presence.userId(), presence.status(), presence.lastHeartbeat()));
        } catch (Exception e) {
            log.error("Failed to serialize presence info: {}", e.getMessage());
            return Mono.empty();
        }
    }

    private Mono<Void> publishPresenceUpdate(PresenceEvent event) {
        try {
            String json = objectMapper.writeValueAsString(event);
            return redisTemplate.convertAndSend(RedisConfig.PRESENCE_UPDATES_CHANNEL, json).then();
        } catch (Exception e) {
            log.error("Failed to publish presence update: {}", e.getMessage());
            return Mono.empty();
        }
    }

    private Mono<Void> updateUserStatusInDb(String userId, UserStatus status, Instant timestamp) {
        return userRepository.findById(userId)
                .flatMap(user -> {
                    user.setStatus(status);
                    user.setLastSeenAt(timestamp);
                    return userRepository.save(user);
                })
                .then();
    }
}
