package com.relay.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.relay.domain.User;
import com.relay.domain.UserStatus;
import com.relay.repository.UserRepository;
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
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PresenceServiceTest {

    @Mock
    private ReactiveStringRedisTemplate redisTemplate;

    @Mock
    private ReactiveValueOperations<String, String> valueOperations;

    @Mock
    private UserRepository userRepository;

    private PresenceService presenceService;
    private final ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());

    @BeforeEach
    void setUp() {
        presenceService = new PresenceService(redisTemplate, userRepository, objectMapper);
    }

    @Test
    @DisplayName("Should mark user ONLINE and publish update")
    void testMarkOnline() {
        String userId = "user-online-1";
        User user = new User();
        user.setId(userId);
        user.setUsername("mwangi");

        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.set(eq("presence:" + userId), anyString(), any(Duration.class))).thenReturn(Mono.just(true));
        when(redisTemplate.convertAndSend(eq("presence:updates"), anyString())).thenReturn(Mono.just(1L));
        when(userRepository.findById(userId)).thenReturn(Mono.just(user));
        when(userRepository.save(any(User.class))).thenReturn(Mono.just(user));

        StepVerifier.create(presenceService.markOnline(userId))
                .verifyComplete();
    }

    @Test
    @DisplayName("Should handle expired Redis presence key and broadcast OFFLINE")
    void testHandleExpiredKey() {
        String userId = "user-expired-99";
        String expiredKey = "presence:" + userId;
        User user = new User();
        user.setId(userId);

        when(redisTemplate.delete(expiredKey)).thenReturn(Mono.just(1L));
        when(redisTemplate.convertAndSend(eq("presence:updates"), anyString())).thenReturn(Mono.just(1L));
        when(userRepository.findById(userId)).thenReturn(Mono.just(user));
        when(userRepository.save(any(User.class))).thenReturn(Mono.just(user));

        StepVerifier.create(presenceService.handleExpiredKey(expiredKey))
                .verifyComplete();
    }
}
