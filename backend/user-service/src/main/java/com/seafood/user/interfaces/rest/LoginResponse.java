package com.seafood.user.interfaces.rest;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "登录响应")
public class LoginResponse {

    @Schema(description = "访问令牌", example = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...")
    private String token;

    @Schema(description = "用户ID", example = "507f1f77bcf86cd799439011")
    private String userId;

    @Schema(description = "用户昵称", example = "用户昵称")
    private String nickname;

    @Schema(description = "用户头像URL", example = "https://example.com/avatar.jpg")
    private String avatarUrl;

    @Schema(description = "用户角色", example = "USER")
    private String role;

    @Schema(description = "token类型", example = "Bearer")
    private String tokenType = "Bearer";

    @Schema(description = "过期时间（秒）", example = "86400")
    private Long expiresIn = 86400L;

    @Schema(description = "刷新令牌", example = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...")
    private String refreshToken;

    @Schema(description = "刷新令牌过期时间（秒）", example = "604800")
    private Long refreshExpiresIn = 604800L;

    /**
     * 构造函数（不含刷新令牌）
     */
    public LoginResponse(String token, String userId, String nickname, String avatarUrl, String role) {
        this.token = token;
        this.userId = userId;
        this.nickname = nickname;
        this.avatarUrl = avatarUrl;
        this.role = role;
        this.tokenType = "Bearer";
        this.expiresIn = 86400L;
    }

    /**
     * 构造函数（含刷新令牌）
     */
    public LoginResponse(String token, String userId, String nickname, String avatarUrl, String role, String refreshToken) {
        this(token, userId, nickname, avatarUrl, role);
        this.refreshToken = refreshToken;
        this.refreshExpiresIn = 604800L;
    }
}