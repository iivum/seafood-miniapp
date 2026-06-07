package com.seafood.user.api.dto;

import java.time.Instant;

/** 登录/刷新统一响应。 */
public record TokenResponse(
        String accessToken,
        String refreshToken,
        Instant accessTokenExpiresAt,
        Instant refreshTokenExpiresAt,
        String role
) {
}
