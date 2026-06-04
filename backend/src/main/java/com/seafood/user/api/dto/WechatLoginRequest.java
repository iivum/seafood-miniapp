package com.seafood.user.api.dto;

import jakarta.validation.constraints.NotBlank;

/** 小程序登录 — 微信 code 换取 openId。 */
public record WechatLoginRequest(
        @NotBlank String code,
        String nickname,
        String avatarUrl
) {
}
