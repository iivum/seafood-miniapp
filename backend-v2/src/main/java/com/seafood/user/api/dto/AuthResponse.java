package com.seafood.user.api.dto;

public record AuthResponse(
    String accessToken,
    String refreshToken,
    String tokenType,
    long expiresIn,
    String username,
    String role
) {}
