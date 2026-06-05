package com.seafood.shared.security;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;
import java.util.Objects;

/**
 * JWT 配置(参见 design.md §4.1 + Sprint 2 §1.1 fail-fast 校验)。
 *
 * <p>小程序/admin 入口共用同一对 ttl,签名密钥分开:
 * <ul>
 *   <li>{@code secret}      — 小程序 /api/auth/** 签名密钥,≥32 字节</li>
 *   <li>{@code adminSecret} — admin-ui /api/admin/auth/** 签名密钥,≥32 字节,
 *       且必须与 {@code secret} 不同(避免单密钥泄漏即两端失陷)</li>
 * </ul>
 *
 * <p>Sprint 2 起,Spring {@code @Validated} 在 binding 阶段触发 JSR-303;任一规则违反
 * 直接抛 {@code ConfigurationPropertiesBindException},进程退出非 0,优先于
 * {@code JwtTokenProvider.@PostConstruct} 的手写兜底。
 */
@ConfigurationProperties(prefix = "security.jwt")
@Validated
public class JwtProperties {

    /** 缺失即 fail-fast(由 @Validated + JwtTokenProvider 双重保险)。 */
    @NotBlank(message = "security.jwt.secret must not be blank")
    @Size(min = 32, message = "security.jwt.secret must be at least 32 bytes (HS256 minimum)")
    private String secret;

    @NotBlank(message = "security.jwt.admin-secret must not be blank")
    @Size(min = 32, message = "security.jwt.admin-secret must be at least 32 bytes (HS256 minimum)")
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

    /**
     * 跨字段校验:adminSecret 不得等于 secret。
     *
     * <p>用 {@code @AssertTrue} 而非自定义校验器,Spring/Hibernate Validator 原生支持,
     * 无须额外组件;命名为 {@code isAdminSecretDistinctFromUserSecret} 避免与可写属性
     * 冲突(只读 boolean 属性会出现在 /actuator/configprops 里,这一布尔不属敏感字段)。
     */
    @AssertTrue(message = "security.jwt.admin-secret must differ from security.jwt.secret")
    public boolean isAdminSecretDistinctFromUserSecret() {
        // null 安全:任一为 null 时把跨字段校验放过,由 @NotBlank 给出准确错误。
        if (secret == null || adminSecret == null) {
            return true;
        }
        return !Objects.equals(secret, adminSecret);
    }
}
