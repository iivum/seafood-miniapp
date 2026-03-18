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
    
    @Schema(description = "访问令牌", example = "TOKEN_1234567890abcdef")
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
}