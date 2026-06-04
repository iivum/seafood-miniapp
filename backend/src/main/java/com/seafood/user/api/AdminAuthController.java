package com.seafood.user.api;

import com.seafood.user.api.dto.AdminLoginRequest;
import com.seafood.user.api.dto.RefreshRequest;
import com.seafood.user.api.dto.TokenResponse;
import com.seafood.user.application.AuthService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Admin UI 入口 — 用户名/密码 + refresh(独立签名密钥)。 */
@RestController
@RequestMapping("/api/admin/auth")
public class AdminAuthController {

    private final AuthService auth;

    public AdminAuthController(AuthService auth) {
        this.auth = auth;
    }

    @PostMapping("/login")
    public ResponseEntity<TokenResponse> login(@Valid @RequestBody AdminLoginRequest req) {
        TokenResponse body = auth.adminLogin(req);
        // 前端可走 cookie(由后续阶段配置 SameSite=httpOnly);此处 JSON 返 token 也可工作
        return ResponseEntity.ok().body(body);
    }

    @PostMapping("/refresh")
    public TokenResponse refresh(@Valid @RequestBody RefreshRequest req) {
        return auth.refresh(req.refreshToken(), AuthService.Audience.ADMIN);
    }
}
