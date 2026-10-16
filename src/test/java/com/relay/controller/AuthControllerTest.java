package com.relay.controller;

import com.relay.config.SecurityConfig;
import com.relay.dto.AuthResponse;
import com.relay.dto.LoginRequest;
import com.relay.dto.RegisterRequest;
import com.relay.dto.UserResponse;
import com.relay.exception.GlobalExceptionHandler;
import com.relay.security.JwtAuthenticationManager;
import com.relay.security.JwtServerAuthenticationConverter;
import com.relay.security.JwtService;
import com.relay.service.AuthService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.WebFluxTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.reactive.server.WebTestClient;
import reactor.core.publisher.Mono;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@WebFluxTest(controllers = AuthController.class)
@Import({SecurityConfig.class, JwtAuthenticationManager.class, JwtServerAuthenticationConverter.class, GlobalExceptionHandler.class})
class AuthControllerTest {

    @Autowired
    private WebTestClient webTestClient;

    @MockBean
    private AuthService authService;

    @MockBean
    private JwtService jwtService;

    @Test
    @DisplayName("POST /auth/register should return 201 Created with JWT and user response")
    void testRegisterSuccess() {
        RegisterRequest request = new RegisterRequest(
                "kamau",
                "Kamau Njoroge",
                "kamau@sme.ke",
                "secretPassword123",
                "https://avatar.url/kamau.png"
        );

        UserResponse userResponse = new UserResponse(
                "u-1", "kamau", "Kamau Njoroge", "kamau@sme.ke",
                "https://avatar.url/kamau.png", null, null, null
        );

        AuthResponse authResponse = AuthResponse.of("mock-jwt-token", 72 * 3600, userResponse);
        when(authService.register(any(RegisterRequest.class))).thenReturn(Mono.just(authResponse));

        webTestClient.post()
                .uri("/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(request)
                .exchange()
                .expectStatus().isCreated()
                .expectBody()
                .jsonPath("$.token").isEqualTo("mock-jwt-token")
                .jsonPath("$.tokenType").isEqualTo("Bearer")
                .jsonPath("$.user.username").isEqualTo("kamau");
    }

    @Test
    @DisplayName("POST /auth/login should return 200 OK with JWT token")
    void testLoginSuccess() {
        LoginRequest request = new LoginRequest("kamau", "secretPassword123");
        UserResponse userResponse = new UserResponse(
                "u-1", "kamau", "Kamau Njoroge", "kamau@sme.ke",
                null, null, null, null
        );

        AuthResponse authResponse = AuthResponse.of("mock-jwt-token", 72 * 3600, userResponse);
        when(authService.login(any(LoginRequest.class))).thenReturn(Mono.just(authResponse));

        webTestClient.post()
                .uri("/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(request)
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.token").isEqualTo("mock-jwt-token")
                .jsonPath("$.user.displayName").isEqualTo("Kamau Njoroge");
    }

    @Test
    @DisplayName("POST /auth/register with invalid payload should return 400 Bad Request")
    void testRegisterValidationFailure() {
        RegisterRequest invalidRequest = new RegisterRequest(
                "", // Blank username
                "",
                "not-an-email",
                "123", // Too short password
                null
        );

        webTestClient.post()
                .uri("/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(invalidRequest)
                .exchange()
                .expectStatus().isBadRequest()
                .expectBody()
                .jsonPath("$.status").isEqualTo(400);
    }
}
