package com.seafood.admin.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class JwtServiceTest {

    private JwtService jwtService;

    @BeforeEach
    void setUp() {
        jwtService = new JwtService("my-super-secret-key-that-is-at-least-32-chars-long-for-hs256", 86400000L);
    }

    @Test
    @DisplayName("generateToken creates valid JWT token")
    void generateToken_createsValidToken() {
        String token = jwtService.generateToken("admin", "ADMIN");

        assertThat(token).isNotBlank();
        assertThat(jwtService.validateToken(token)).isTrue();
    }

    @Test
    @DisplayName("extractUsername returns correct username")
    void extractUsername_returnsCorrectUsername() {
        String token = jwtService.generateToken("testuser", "USER");

        String username = jwtService.extractUsername(token);

        assertThat(username).isEqualTo("testuser");
    }

    @Test
    @DisplayName("extractRole returns correct role")
    void extractRole_returnsCorrectRole() {
        String token = jwtService.generateToken("admin", "ADMIN");

        String role = jwtService.extractRole(token);

        assertThat(role).isEqualTo("ADMIN");
    }

    @Test
    @DisplayName("validateToken returns false for invalid token")
    void validateToken_returnsFalseForInvalidToken() {
        assertThat(jwtService.validateToken("invalid.token.here")).isFalse();
    }

    @Test
    @DisplayName("validateToken returns false for malformed token")
    void validateToken_returnsFalseForMalformedToken() {
        assertThat(jwtService.validateToken("not-a-jwt")).isFalse();
    }

    @Test
    @DisplayName("validateToken returns false for null token")
    void validateToken_returnsFalseForNullToken() {
        assertThat(jwtService.validateToken(null)).isFalse();
    }
}
