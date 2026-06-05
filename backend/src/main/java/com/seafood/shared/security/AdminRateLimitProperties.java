package com.seafood.shared.security;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Admin 路径限流配置(Sprint 2 §2.4,specs/runtime-security §Admin endpoints enforce
 * a rate limit,design §4 decision)。
 *
 * <p>默认 60 rpm,够 admin-ui 正常使用 1 个数量级,挡 brute-force 撞库与爬取;
 * Customer-facing {@code /api/products} 不在限流范围(spec scenarios)。
 */
@ConfigurationProperties(prefix = "security.rate-limit")
@Validated
public class AdminRateLimitProperties {

    /** 每分钟允许请求数。 */
    private int requestsPerMinute = 60;

    /**
     * 桶条目在 Caffeine 里的过期时间(秒)。空桶持续空闲后被回收,防内存泄漏;
     * 设大于 60 s 以容忍时钟漂移,设小于过大值以防恶意 IP 占内存。
     */
    private int bucketTtlSeconds = 120;

    public int getRequestsPerMinute() { return requestsPerMinute; }
    public void setRequestsPerMinute(int v) { this.requestsPerMinute = v; }

    public int getBucketTtlSeconds() { return bucketTtlSeconds; }
    public void setBucketTtlSeconds(int v) { this.bucketTtlSeconds = v; }
}
