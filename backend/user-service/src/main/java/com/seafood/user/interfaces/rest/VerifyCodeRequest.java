package com.seafood.user.interfaces.rest;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
@Schema(description = "获取验证码请求")
public class VerifyCodeRequest {
    
    @Schema(description = "手机号", example = "13800138000", required = true)
    private String phone;
    
    @Schema(description = "验证码类型", example = "login", allowableValues = {"login", "register", "reset"})
    private String codeType = "login";
}