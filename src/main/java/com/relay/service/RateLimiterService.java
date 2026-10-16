package com.relay.service;

import com.relay.exception.RateLimitExceededException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.time.Duration;

@Service
public class RateLimiterService {

    private static final Logger log = LoggerFactory.getLogger(RateLimiterService.class);

    private static final int MAX_MESSAGES_PER_MINUTE = 20;
    private static final int MAX_CONVERSATIONS_PER_DAY = 5;

    private static final Duration MESSAGE_WINDOW = Duration.ofMinutes(1);
    private static final Duration CONVERSATION_WINDOW = Duration.ofDays(1);

    private final ReactiveStringRedisTemplate redisTemplate;

    public RateLimiterService(ReactiveStringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    public Mono<Void> checkMessageRateLimit(String userId) {
        String key = "ratelimit:messages:" + userId;
        return checkLimit(key, MAX_MESSAGES_PER_MINUTE, MESSAGE_WINDOW, "MESSAGES_PER_MINUTE");
    }

    public Mono<Void> checkConversationCreationRateLimit(String userId) {
        String key = "ratelimit:conversations:" + userId;
        return checkLimit(key, MAX_CONVERSATIONS_PER_DAY, CONVERSATION_WINDOW, "CONVERSATIONS_PER_DAY");
    }

    private Mono<Void> checkLimit(String key, int maxAllowed, Duration window, String limitType) {
        return redisTemplate.opsForValue().increment(key)
                .flatMap(currentCount -> {
                    if (currentCount == 1) {
                        // First request in window, set TTL
                        return redisTemplate.expire(key, window)
                                .thenReturn(currentCount);
                    }
                    return Mono.just(currentCount);
                })
                .flatMap(currentCount -> {
                    if (currentCount > maxAllowed) {
                        return redisTemplate.getExpire(key)
                                .defaultIfEmpty(window)
                                .flatMap(remainingTtl -> {
                                    long retryAfterSec = remainingTtl.isNegative() ? window.toSeconds() : remainingTtl.toSeconds();
                                    log.warn("Rate limit exceeded for user on key {}: count={}, limit={}", key, currentCount, maxAllowed);
                                    return Mono.error(new RateLimitExceededException(
                                            "Rate limit of " + maxAllowed + " exceeded for " + limitType + ". Try again in " + retryAfterSec + " seconds.",
                                            limitType,
                                            Math.max(1, retryAfterSec)
                                    ));
                                });
                    }
                    return Mono.empty();
                });
    }
}
