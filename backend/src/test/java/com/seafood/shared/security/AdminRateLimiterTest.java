package com.seafood.shared.security;

import com.github.benmanes.caffeine.cache.Ticker;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Sprint 2 §2.5 — {@link AdminRateLimiter} 固定窗口限流。
 *
 * <p>用假 {@link Ticker} 推进时间,验证:
 * <ul>
 *   <li>窗口内 60 次全过</li>
 *   <li>第 61 次拒绝并返回正确的 Retry-After</li>
 *   <li>切到下一个 60s 窗口后,计数重置</li>
 *   <li>不同 key 各自独立</li>
 * </ul>
 */
class AdminRateLimiterTest {

    private static final AdminRateLimitProperties DEFAULT_PROPS;
    static {
        DEFAULT_PROPS = new AdminRateLimitProperties();
        DEFAULT_PROPS.setRequestsPerMinute(60);
        DEFAULT_PROPS.setBucketTtlSeconds(120);
    }

    @Test
    void firstSixtyRequestsInSameWindowAreAllowed() {
        FakeTicker ticker = new FakeTicker();
        AdminRateLimiter limiter = new AdminRateLimiter(DEFAULT_PROPS, ticker);

        for (int i = 0; i < 60; i++) {
            assertThat(limiter.tryAcquire("ip1:user1").permitted())
                    .as("request #%d in the first window", i + 1)
                    .isTrue();
        }
    }

    @Test
    void sixtyFirstRequestIsDeniedWithRetryAfterEqualToRemainingSeconds() {
        FakeTicker ticker = new FakeTicker();
        AdminRateLimiter limiter = new AdminRateLimiter(DEFAULT_PROPS, ticker);

        for (int i = 0; i < 60; i++) {
            limiter.tryAcquire("ip1:user1");
        }
        // 走到窗口中段,Retry-After 应为剩下秒数
        ticker.advance(15_000);

        AdminRateLimiter.Decision d = limiter.tryAcquire("ip1:user1");
        assertThat(d.permitted()).isFalse();
        // 已用 15s,剩 45s;上取整 45
        assertThat(d.retryAfterSeconds()).isEqualTo(45);
    }

    @Test
    void advancingPastWindowResetsTheCounter() {
        FakeTicker ticker = new FakeTicker();
        AdminRateLimiter limiter = new AdminRateLimiter(DEFAULT_PROPS, ticker);

        for (int i = 0; i < 60; i++) {
            limiter.tryAcquire("ip1:user1");
        }
        assertThat(limiter.tryAcquire("ip1:user1").permitted()).isFalse();

        // 跨过 60s 边界
        ticker.advance(60_000);
        assertThat(limiter.tryAcquire("ip1:user1").permitted())
                .as("first request in the new window")
                .isTrue();
    }

    @Test
    void separateKeysHaveSeparateBuckets() {
        FakeTicker ticker = new FakeTicker();
        AdminRateLimiter limiter = new AdminRateLimiter(DEFAULT_PROPS, ticker);

        // user1 用完
        for (int i = 0; i < 60; i++) {
            limiter.tryAcquire("ip:user1");
        }
        assertThat(limiter.tryAcquire("ip:user1").permitted()).isFalse();
        // user2 仍可用
        assertThat(limiter.tryAcquire("ip:user2").permitted()).isTrue();
    }

    /** 手动时间推进器;{@link Ticker#read()} 始终返回 0 + 偏移。 */
    private static final class FakeTicker implements Ticker {
        private volatile long elapsedNanos = 0L;

        @Override
        public long read() {
            return elapsedNanos;
        }

        void advance(long millis) {
            elapsedNanos += millis * 1_000_000L;
        }
    }
}
