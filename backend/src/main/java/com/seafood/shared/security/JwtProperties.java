package com.seafood.shared.security;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * JWT 配置(参见 design.md §4.1)。
 *
 * <p>小程序/admin 入口共用同一对 ttl,签名密钥分开:
 * <ul>
 *   <li>{@code secret}    — 小程序 /api/auth/** 签名密钥</li>
 *   <li>{@code adminSecret} — admin-ui /api/admin/auth/** 签名密钥</li>
 * </ul>
 * 两者启动时校验,缺失即 fail-fast。
 */
@ConfigurationProperties(prefix = "security.jwt")
public class JwtProperties {

    /** 缺失即 fail-fast(由 JwtTokenProvider 在 @PostConstruct 校验)。 */
    private String secret;
    private String adminSecret;
    private Duration accessTokenTtl = Duration.ofMinutes(15);
    private Duration refreshTokenTtl = Duration.ofDays(7);

    public String getSecret() { return secret; }
    public void setSecret(String secret) { this.secret = secret; }

    public String getAdminSecret() { return adminSecret; }
    public void setAdminSecret(String adminSecret) { this.adminSecret = adminSecret; }

    public Duration getAccessTokenTtl() { return accessTokenTtl; }
    public void setAccessTokenTtl(Duration accessTokenTtl) { this.accessTokenTtl = accessTokenTtl; }

    public Duration getRefreshTokenTtl() { return refreshTokenTtl; }
    public void setRefreshTokenTtl(Duration refreshTokenTtl) { this.refreshTokenTtl = refreshTokenTtl; }
}
