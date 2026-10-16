package com.relay.security;

import io.jsonwebtoken.Claims;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class JwtServiceTest {

    private JwtService jwtService;

    @BeforeEach
    void setUp() {
        jwtService = new JwtService(
                "my-super-secret-test-key-which-is-sufficiently-long-for-hmac-sha-256",
                24
        );
    }

    @Test
    @DisplayName("Should generate valid JWT and extract correct claims")
    void testGenerateAndValidateToken() {
        String userId = "user-12345";
        String username = "mwangi";
        String email = "mwangi@sme.ke";

        String token = jwtService.generateToken(userId, username, email);
        assertThat(token).isNotBlank();

        assertThat(jwtService.isValid(token)).isTrue();
        assertThat(jwtService.extractUserId(token)).isEqualTo(userId);
        assertThat(jwtService.extractUsername(token)).isEqualTo(username);

        Claims claims = jwtService.parseAndValidate(token);
        assertThat(claims).isNotNull();
        assertThat(claims.get("email")).isEqualTo(email);
    }

    @Test
    @DisplayName("Should reject invalid or tampered tokens")
    void testTamperedToken() {
        String token = jwtService.generateToken("user-1", "test", "test@example.com");
        String tamperedToken = token + "xyz";

        assertThat(jwtService.isValid(tamperedToken)).isFalse();
        assertThat(jwtService.parseAndValidate(tamperedToken)).isNull();
    }
}
