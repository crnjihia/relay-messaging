package com.relay.service;

import com.relay.domain.User;
import com.relay.domain.UserStatus;
import com.relay.dto.UserResponse;
import com.relay.exception.ResourceNotFoundException;
import com.relay.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.Collection;

@Service
public class UserService {

    private static final Logger log = LoggerFactory.getLogger(UserService.class);

    private final UserRepository userRepository;

    public UserService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    public Mono<UserResponse> getUserById(String id) {
        return userRepository.findById(id)
                .switchIfEmpty(Mono.error(new ResourceNotFoundException("User not found: " + id)))
                .map(UserResponse::fromDomain);
    }

    public Flux<UserResponse> getUsersByIds(Collection<String> ids) {
        return userRepository.findByIdIn(ids)
                .map(UserResponse::fromDomain);
    }

    public Mono<Void> updateFcmToken(String userId, String fcmToken) {
        return userRepository.findById(userId)
                .switchIfEmpty(Mono.error(new ResourceNotFoundException("User not found: " + userId)))
                .flatMap(user -> {
                    user.setFcmToken(fcmToken);
                    return userRepository.save(user);
                })
                .doOnSuccess(u -> log.info("Updated FCM token for user: {}", userId))
                .then();
    }

    public Mono<User> updateUserStatus(String userId, UserStatus status) {
        return userRepository.findById(userId)
                .flatMap(user -> {
                    user.setStatus(status);
                    user.setLastSeenAt(Instant.now());
                    return userRepository.save(user);
                });
    }
}
