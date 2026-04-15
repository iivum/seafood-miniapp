package com.seafood.common.security;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * JWT配置属性类
 * 从application.yml中读取JWT相关配置
 */
@ConfigurationProperties(prefix = "jwt")
public class JwtProperties {

    /**
     * JWT密钥（Base64编码）
     * 推荐使用HS256算法，密钥长度至少256位
     */
    private String secret;

    /**
     * 令牌过期时间（毫秒）
     * 默认：24小时
     */
    private long expiration = 86400000L;

    /**
     * 刷新令牌过期时间（毫秒）
     * 默认：7天
     */
    private long refreshExpiration = 604800000L;

    /**
     * 令牌签发者
     */
    private String issuer = "seafood-miniapp";

    /**
     * 令牌接收者
     */
    private String audience = "seafood-users";

    public String getSecret() {
        return secret;
    }

    public void setSecret(String secret) {
        this.secret = secret;
    }

    public long getExpiration() {
        return expiration;
    }

    public void setExpiration(long expiration) {
        this.expiration = expiration;
    }

    public long getRefreshExpiration() {
        return refreshExpiration;
    }

    public void setRefreshExpiration(long refreshExpiration) {
        this.refreshExpiration = refreshExpiration;
    }

    public String getIssuer() {
        return issuer;
    }

    public void setIssuer(String issuer) {
        this.issuer = issuer;
    }

    public String getAudience() {
        return audience;
    }

    public void setAudience(String audience) {
        this.audience = audience;
    }
}