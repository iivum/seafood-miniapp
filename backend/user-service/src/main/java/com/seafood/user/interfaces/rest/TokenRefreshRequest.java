package com.seafood.user.interfaces.rest;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Request payload for refreshing an expired access token.
 * The refresh token is provided during login and has a longer
 * lifetime than the access token.
 *
 * <p>Refresh tokens are typically valid for 7 days while access
 * tokens are valid for 24 hours. When the access token expires,
 * clients should use this endpoint to obtain a new one without
 * requiring the user to re-authenticate.</p>
 *
 * @see TokenRefreshResponse
 * @see LoginResponse
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Request to refresh access token")
public class TokenRefreshRequest {

    @NotBlank(message = "Refresh token is required")
    @Schema(description = "Refresh token obtained during login", example = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...")
    private String refreshToken;
}
