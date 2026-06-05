package com.seafood.shared.security;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.Ticker;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Admin 路径限流器(Sprint 2 §2.5,specs/runtime-security §Admin endpoints enforce a
 * rate limit,design §4 decision 4)。
 *
 * <p>算法:固定窗口 + Caffeine 内存计数。每个 {@code (clientIp + ":" + account)} 桶
 * 维护当前窗口开始时间(毫秒)与已用计数;窗口 60s,每桶每窗口最多
 * {@code requestsPerMinute} 次。超过则拒绝并返回距下次刷新的秒数。
 *
 * <p>为什么不用 {@code Bucket4j}:只此一处用,引一个 dep 不值。spec §Open Questions
 * 提到 GraalVM Native 兼容性;Caffeine 已被 Spring Boot 4 reachability metadata 覆盖。
 *
 * <p>所有时间计算统一用毫秒,避免 nanos/millis 单位混淆。
 */
@Component
public class AdminRateLimiter {

    private static final long WINDOW_MS = 60_000L;

    private final AdminRateLimitProperties props;
    private final Cache<String, AtomicReference<Window>> buckets;
    private final Ticker ticker;

    public AdminRateLimiter(AdminRateLimitProperties props) {
        this(props, Ticker.systemTicker());
    }

    /** 测试用:注入假 {@link Ticker} 以快进时间。 */
    AdminRateLimiter(AdminRateLimitProperties props, Ticker ticker) {
        this.props = props;
        this.ticker = ticker;
        this.buckets = Caffeine.newBuilder()
                .expireAfterAccess(Duration.ofSeconds(props.getBucketTtlSeconds()))
                .ticker(ticker)
                .build();
    }

    public Decision tryAcquire(String key) {
        AtomicReference<Window> ref = buckets.get(key,
                k -> new AtomicReference<>(new Window(currentWindowStartMs(), 0)));
        Window w = ref.get();
        long nowMs = currentTimeMs();
        long windowStart = currentWindowStartMs();
        if (w.startMs != windowStart) {
            // 切到新窗口
            Window fresh = new Window(windowStart, 0);
            if (!ref.compareAndSet(w, fresh)) {
                w = ref.get();
            } else {
                w = fresh;
            }
        }
        if (w.count >= props.getRequestsPerMinute()) {
            long retryMs = (w.startMs + WINDOW_MS) - nowMs;
            int retrySec = (int) Math.max(1L, (retryMs + 999) / 1000); // 上取整,至少 1
            return Decision.deny(retrySec);
        }
        Window bumped = new Window(w.startMs, w.count + 1);
        if (!ref.compareAndSet(w, bumped)) {
            bumped = ref.get();
        }
        return Decision.grant();
    }

    /** 当前时间(毫秒);把 Ticker 的 nanos 读数转换成毫秒。 */
    private long currentTimeMs() {
        return ticker.read() / 1_000_000L;
    }

    /** 当前 60 秒窗口起点(毫秒);用整数除法对齐到 60s 边界。 */
    private long currentWindowStartMs() {
        long nowMs = currentTimeMs();
        return (nowMs / WINDOW_MS) * WINDOW_MS;
    }

    public record Decision(boolean permitted, int retryAfterSeconds) {
        public static Decision grant() { return new Decision(true, 0); }
        public static Decision deny(int retryAfterSeconds) {
            return new Decision(false, retryAfterSeconds);
        }
    }

    private record Window(long startMs, int count) {}
}
