package com.relay.controller;

import com.relay.dto.UpdateFcmTokenRequest;
import com.relay.dto.UserResponse;
import com.relay.service.AuthService;
import com.relay.service.UserService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

@RestController
@RequestMapping("/users")
public class UserController {

    private final UserService userService;
    private final AuthService authService;

    public UserController(UserService userService, AuthService authService) {
        this.userService = userService;
        this.authService = authService;
    }

    @GetMapping("/me")
    public Mono<UserResponse> getMe(Authentication authentication) {
        String currentUserId = authentication.getName();
        return authService.getCurrentUser(currentUserId);
    }

    @GetMapping("/{id}")
    public Mono<UserResponse> getUserById(@PathVariable String id) {
        return userService.getUserById(id);
    }

    @PostMapping("/me/fcm-token")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public Mono<Void> updateFcmToken(Authentication authentication,
                                     @Valid @RequestBody UpdateFcmTokenRequest request) {
        String currentUserId = authentication.getName();
        return userService.updateFcmToken(currentUserId, request.fcmToken());
    }
}
