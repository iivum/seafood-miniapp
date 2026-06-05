package com.seafood.user.application;

/**
 * 账户被锁定的业务异常(Sprint 2 §3.10,specs/auth §Login lockout)。
 *
 * <p>由 {@link LoginAttemptService} 在 {@code isLocked} 命中时抛出,由
 * {@code GlobalExceptionHandler} 翻译为 HTTP 423 + {@code code=ACCOUNT_LOCKED}
 * + {@code Retry-After} 头。
 *
 * <p>{@code retryAfterSeconds} 单位秒,由锁到期时间减去当前时间换算,前端可
 * 用来显示"还剩 N 秒可重试"。
 */
public class AccountLockedException extends RuntimeException {

    private final int retryAfterSeconds;

    public AccountLockedException(int retryAfterSeconds) {
        super("Account is temporarily locked; retry after " + retryAfterSeconds + "s");
        this.retryAfterSeconds = retryAfterSeconds;
    }

    public int getRetryAfterSeconds() {
        return retryAfterSeconds;
    }
}
