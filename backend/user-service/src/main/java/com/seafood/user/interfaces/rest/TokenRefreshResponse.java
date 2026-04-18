package com.seafood.user.interfaces.rest;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Response payload returned when refreshing an access token.
 * Contains a new access token (and optionally a new refresh token)
 * that the client should use for subsequent API requests.
 *
 * @see TokenRefreshRequest
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Response containing new access and refresh tokens")
public class TokenRefreshResponse {

    @Schema(description = "新访问令牌", example = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...")
    private String accessToken;

    @Schema(description = "新刷新令牌（可选）", example = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...")
    private String refreshToken;

    @Schema(description = "令牌类型", example = "Bearer")
    private String tokenType;

    @Schema(description = "访问令牌过期时间（秒）", example = "3600")
    private long expiresIn;
}
