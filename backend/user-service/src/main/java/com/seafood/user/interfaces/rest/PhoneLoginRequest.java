package com.seafood.user.interfaces.rest;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
@Schema(description = "手机号登录请求")
public class PhoneLoginRequest {
    
    @Schema(description = "手机号", example = "13800138000", required = true)
    private String phone;
    
    @Schema(description = "验证码", example = "123456", required = true)
    private String verifyCode;
    
    @Schema(description = "密码（注册时使用）", example = "password123")
    private String password;
    
    @Schema(description = "登录模式", example = "login", allowableValues = {"login", "register"})
    private String loginMode = "login";
}