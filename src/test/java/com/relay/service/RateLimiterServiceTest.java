package com.relay.service;

import com.relay.exception.RateLimitExceededException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.data.redis.core.ReactiveValueOperations;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.time.Duration;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RateLimiterServiceTest {

    @Mock
    private ReactiveStringRedisTemplate redisTemplate;

    @Mock
    private ReactiveValueOperations<String, String> valueOperations;

    private RateLimiterService rateLimiterService;

    @BeforeEach
    void setUp() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        rateLimiterService = new RateLimiterService(redisTemplate);
    }

    @Test
    @DisplayName("Should allow message when count is within 20 messages/min")
    void testMessageRateLimitWithinBound() {
        String userId = "user-abc";
        String key = "ratelimit:messages:" + userId;

        when(valueOperations.increment(key)).thenReturn(Mono.just(1L));
        when(redisTemplate.expire(eq(key), any(Duration.class))).thenReturn(Mono.just(true));

        StepVerifier.create(rateLimiterService.checkMessageRateLimit(userId))
                .verifyComplete();
    }

    @Test
    @DisplayName("Should throw RateLimitExceededException when message count exceeds 20")
    void testMessageRateLimitExceeded() {
        String userId = "user-spam";
        String key = "ratelimit:messages:" + userId;

        when(valueOperations.increment(key)).thenReturn(Mono.just(21L));
        when(redisTemplate.getExpire(key)).thenReturn(Mono.just(Duration.ofSeconds(45)));

        StepVerifier.create(rateLimiterService.checkMessageRateLimit(userId))
                .expectError(RateLimitExceededException.class)
                .verify();
    }

    @Test
    @DisplayName("Should throw RateLimitExceededException when conversation count exceeds 5 per day")
    void testConversationCreationLimitExceeded() {
        String userId = "user-burst";
        String key = "ratelimit:conversations:" + userId;

        when(valueOperations.increment(key)).thenReturn(Mono.just(6L));
        when(redisTemplate.getExpire(key)).thenReturn(Mono.just(Duration.ofHours(12)));

        StepVerifier.create(rateLimiterService.checkConversationCreationRateLimit(userId))
                .expectError(RateLimitExceededException.class)
                .verify();
    }
}
