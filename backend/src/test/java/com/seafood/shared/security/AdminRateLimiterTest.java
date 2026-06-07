package com.seafood.shared.security;

import com.github.benmanes.caffeine.cache.Ticker;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

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
 *   <li>并发场景下 CAS race 不会让 grant 数超过 limit(PR review #3 回归保护)</li>
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

    /**
     * PR review #3 回归测试:N 个并发线程同时打到同一桶,grant 数必须 ≤ limit。
     *
     * <p>原 bug:CAS 失败时 {@code bumped = ref.get()} 重读但不重检 limit,然后无条件
     * {@code Decision.grant()} — 两个并发请求都看到 count=N-1,一个 CAS 成功 (count=N),
     * 另一个 CAS 失败但仍 grant,桶溢出。
     *
     * <p>用 200 线程同时打一个 60 rpm 桶,grant 数应严格 ≤ 60。
     */
    @Test
    void concurrentRequestsDoNotExceedLimit() throws InterruptedException {
        FakeTicker ticker = new FakeTicker();
        AdminRateLimiter limiter = new AdminRateLimiter(DEFAULT_PROPS, ticker);
        String key = "ip:contended-user";

        int threadCount = 200;
        CountDownLatch startGate = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(threadCount);
        AtomicInteger granted = new AtomicInteger();
        AtomicInteger denied = new AtomicInteger();

        ExecutorService pool = Executors.newFixedThreadPool(threadCount);
        try {
            for (int i = 0; i < threadCount; i++) {
                pool.submit(() -> {
                    try {
                        startGate.await();
                        AdminRateLimiter.Decision d = limiter.tryAcquire(key);
                        if (d.permitted()) {
                            granted.incrementAndGet();
                        } else {
                            denied.incrementAndGet();
                        }
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    } finally {
                        done.countDown();
                    }
                });
            }
            startGate.countDown();
            assertThat(done.await(10, TimeUnit.SECONDS))
                    .as("all %d worker threads should complete within 10s", threadCount)
                    .isTrue();
        } finally {
            pool.shutdownNow();
        }

        assertThat(granted.get() + denied.get()).isEqualTo(threadCount);
        assertThat(granted.get())
                .as("grant count must NOT exceed the limit even under contention (rpm=%d)",
                        DEFAULT_PROPS.getRequestsPerMinute())
                .isLessThanOrEqualTo(DEFAULT_PROPS.getRequestsPerMinute());
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
