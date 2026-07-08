package com.seafood.user.api.dto;

import jakarta.validation.constraints.NotBlank;

/** 手机号绑定请求 —— {@code code} 为微信 {@code getPhoneNumber} 授权换取的 code。 */
public record PhoneBindRequest(
        @NotBlank String code
) {
}
