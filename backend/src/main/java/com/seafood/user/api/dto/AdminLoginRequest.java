package com.seafood.user.api.dto;

import jakarta.validation.constraints.NotBlank;

/** Admin UI 登录 — 用户名/密码(独立密钥)。 */
public record AdminLoginRequest(
        @NotBlank String username,
        @NotBlank String password
) {
}
