package com.relay.service;

import com.relay.domain.User;
import com.relay.domain.UserStatus;
import com.relay.dto.AuthResponse;
import com.relay.dto.LoginRequest;
import com.relay.dto.RegisterRequest;
import com.relay.dto.UserResponse;
import com.relay.exception.RelayException;
import com.relay.exception.ResourceNotFoundException;
import com.relay.exception.UnauthorizedException;
import com.relay.repository.UserRepository;
import com.relay.security.JwtService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.time.Instant;

@Service
public class AuthService {

    private static final Logger log = LoggerFactory.getLogger(AuthService.class);

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;

    public AuthService(UserRepository userRepository,
                       PasswordEncoder passwordEncoder,
                       JwtService jwtService) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
    }

    public Mono<AuthResponse> register(RegisterRequest request) {
        return userRepository.existsByUsernameIgnoreCase(request.username())
                .flatMap(existsUsername -> {
                    if (existsUsername) {
                        return Mono.error(new RelayException("Username '" + request.username() + "' is already taken"));
                    }
                    return userRepository.existsByEmailIgnoreCase(request.email());
                })
                .flatMap(existsEmail -> {
                    if (existsEmail) {
                        return Mono.error(new RelayException("Email '" + request.email() + "' is already registered"));
                    }

                    User user = new User();
                    user.setUsername(request.username().trim().toLowerCase());
                    user.setDisplayName(request.displayName().trim());
                    user.setEmail(request.email().trim().toLowerCase());
                    user.setPasswordHash(passwordEncoder.encode(request.password()));
                    user.setAvatarUrl(request.avatarUrl());
                    user.setStatus(UserStatus.OFFLINE);
                    user.setCreatedAt(Instant.now());

                    return userRepository.save(user);
                })
                .map(savedUser -> {
                    String token = jwtService.generateToken(savedUser.getId(), savedUser.getUsername(), savedUser.getEmail());
                    log.info("Registered new user: id={}, username={}", savedUser.getId(), savedUser.getUsername());
                    return AuthResponse.of(token, jwtService.getExpirationSeconds(), UserResponse.fromDomain(savedUser));
                });
    }

    public Mono<AuthResponse> login(LoginRequest request) {
        String identifier = request.usernameOrEmail().trim().toLowerCase();
        Mono<User> userMono = identifier.contains("@")
                ? userRepository.findByEmailIgnoreCase(identifier)
                : userRepository.findByUsernameIgnoreCase(identifier);

        return userMono
                .switchIfEmpty(Mono.error(new UnauthorizedException("Invalid username/email or password")))
                .flatMap(user -> {
                    if (!passwordEncoder.matches(request.password(), user.getPasswordHash())) {
                        return Mono.error(new UnauthorizedException("Invalid username/email or password"));
                    }

                    user.setLastSeenAt(Instant.now());
                    return userRepository.save(user);
                })
                .map(user -> {
                    String token = jwtService.generateToken(user.getId(), user.getUsername(), user.getEmail());
                    log.info("User logged in: id={}, username={}", user.getId(), user.getUsername());
                    return AuthResponse.of(token, jwtService.getExpirationSeconds(), UserResponse.fromDomain(user));
                });
    }

    public Mono<UserResponse> getCurrentUser(String userId) {
        return userRepository.findById(userId)
                .switchIfEmpty(Mono.error(new ResourceNotFoundException("User not found: " + userId)))
                .map(UserResponse::fromDomain);
    }
}
