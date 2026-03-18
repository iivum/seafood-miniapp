package com.seafood.user.interfaces.rest;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
@Schema(description = "登录请求")
public class LoginRequest {
    
    @Schema(description = "微信OpenID", example = "oV6Dt123456789")
    private String openId;
    
    @Schema(description = "用户名", example = "user123")
    private String username;
    
    @Schema(description = "密码", example = "password123")
    private String password;
}