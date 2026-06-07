package com.seafood.shared.security;

/**
 * Admin 路径限流触发时抛出的异常(Sprint 2 §2.6,specs/runtime-security §Admin
 * endpoints enforce a rate limit)。
 *
 * <p>由 {@link AdminRateLimitFilter} 在桶耗尽时抛出,由
 * {@code GlobalExceptionHandler} 统一翻译为 HTTP 429 + {@code code=RATE_LIMITED}
 * + {@code Retry-After} 头。
 *
 * <p>不继承 {@code DomainException}(409) — 限流是协议层而非业务层。
 */
public class RateLimitedException extends RuntimeException {

    private final int retryAfterSeconds;

    public RateLimitedException(int retryAfterSeconds) {
        super("Rate limit exceeded; retry after " + retryAfterSeconds + "s");
        this.retryAfterSeconds = retryAfterSeconds;
    }

    public int getRetryAfterSeconds() {
        return retryAfterSeconds;
    }
}
