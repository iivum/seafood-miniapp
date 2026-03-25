package com.seafood.user.interfaces.rest;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 手机号注册请求
 */
@Data
@Schema(description = "手机号注册请求")
public class PhoneRegisterRequest {

    @Schema(description = "手机号", example = "13800138000", required = true)
    private String phone;

    @Schema(description = "验证码", example = "123456", required = true)
    private String verifyCode;

    @Schema(description = "密码", example = "Password123", required = true)
    private String password;

    @Schema(description = "昵称（可选）", example = "新用户")
    private String nickname;
}