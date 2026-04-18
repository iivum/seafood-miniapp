package com.seafood.user.interfaces.rest;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * Request payload for new user registration via phone number.
 * Requires a valid SMS verification code to complete registration.
 *
 * <p>The verification code is typically sent via SMS and must be
 * entered within a short time window (usually 5 minutes).</p>
 *
 * @see PhoneLoginRequest
 */
@Data
@Schema(description = "手机号注册请求")
public class PhoneRegisterRequest {

    @Schema(description = "手机号", example = "13800138000", required = true)
    private String phone;

    @Schema(description = "短信验证码", example = "123456", required = true)
    private String verifyCode;

    @Schema(description = "密码（需包含大小写字母和数字）", example = "Password123", required = true)
    private String password;

    @Schema(description = "昵称（可选，默认使用\"用户\"+手机号后4位）", example = "新用户")
    private String nickname;
}