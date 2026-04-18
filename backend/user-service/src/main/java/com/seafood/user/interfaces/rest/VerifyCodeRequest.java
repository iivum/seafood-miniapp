package com.seafood.user.interfaces.rest;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * Request payload for sending a verification code to a phone number.
 * The verification code is typically sent via SMS for either
 * login verification or account registration.
 *
 * <p>Rate limiting should be enforced on the server side to prevent
 * abuse of the SMS sending capability.</p>
 *
 * @see PhoneLoginRequest
 * @see PhoneRegisterRequest
 */
@Data
@Schema(description = "获取验证码请求")
public class VerifyCodeRequest {

    @Schema(description = "手机号", example = "13800138000", required = true)
    private String phone;

    @Schema(description = "验证码类型: login=登录, register=注册, reset=密码重置", example = "login", allowableValues = {"login", "register", "reset"})
    private String codeType = "login";
}