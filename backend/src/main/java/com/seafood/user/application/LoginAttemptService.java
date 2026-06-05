package com.seafood.user.application;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.Ticker;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicReference;

/**
 * 登录失败计数 + 锁定判定(Sprint 2 §3.9,specs/auth §Login lockout,design §3 decision 3)。
 *
 * <p>数据结构:每个 account(用 userId 或 username 作为 key)维护一个
 * {@link FailureCounter},记录:
 * <ul>
 *   <li>{@code failures} — 滚动窗口内的连续失败次数</li>
 *   <li>{@code firstFailureAtMillis} — 第一次失败时间;窗口起点</li>
 *   <li>{@code lockedUntilMillis} — 锁定到期时间(0 表示未锁)</li>
 * </ul>
 *
 * <p>Caffeine 用 {@code expireAfterAccess(windowMinutes)} 滚动回收未活动 key,
 * 防内存泄漏。
 *
 * <p>所有时间单位用 millis 统一(nanos 容易混淆)。
 */
@Service
public class LoginAttemptService {

    private final LoginAttemptProperties props;
    private final Cache<String, AtomicReference<FailureCounter>> counters;
    private final Ticker ticker;

    public LoginAttemptService(LoginAttemptProperties props) {
        this(props, Ticker.systemTicker());
    }

    /** 测试用:注入假 {@link Ticker} 以快进时间。 */
    LoginAttemptService(LoginAttemptProperties props, Ticker ticker) {
        this.props = props;
        this.ticker = ticker;
        this.counters = Caffeine.newBuilder()
                .expireAfterAccess(Duration.ofMinutes(Math.max(props.getWindowMinutes(), props.getLockMinutes())))
                .ticker(ticker)
                .build();
    }

    /**
     * 检查账号是否被锁;若被锁,返回剩余秒数(供 {@code Retry-After});否则 0。
     */
    public int isLocked(String account) {
        if (account == null || account.isBlank()) return 0;
        AtomicReference<FailureCounter> ref = counters.getIfPresent(account);
        if (ref == null) return 0;
        FailureCounter c = ref.get();
        long nowMs = nowMs();
        if (c.lockedUntilMillis > nowMs) {
            long remain = c.lockedUntilMillis - nowMs;
            return (int) Math.max(1L, (remain + 999) / 1000); // 上取整,至少 1
        }
        return 0;
    }

    /**
     * 记录一次失败;若累计到 {@code maxFailures} 则标记锁定。
     *
     * @return 锁定后的剩余秒数(若刚刚被锁),否则 0
     */
    public int recordFailure(String account) {
        if (account == null || account.isBlank()) return 0;
        long nowMs = nowMs();
        long windowMs = Duration.ofMinutes(props.getWindowMinutes()).toMillis();
        long lockMs = Duration.ofMinutes(props.getLockMinutes()).toMillis();

        AtomicReference<FailureCounter> ref = counters.get(account,
                k -> new AtomicReference<>(new FailureCounter(0, 0L, 0L)));
        for (;;) {
            FailureCounter cur = ref.get();
            FailureCounter next;
            // 锁定期已过 → 解锁 + 计数清零,作为新一次失败
            if (cur.lockedUntilMillis > 0 && cur.lockedUntilMillis <= nowMs) {
                next = new FailureCounter(1, nowMs, 0L);
            }
            // 全新账号(没失败过)或窗口已过 → 重新计数
            else if (cur.failures == 0
                    || (nowMs - cur.firstFailureAtMillis) > windowMs) {
                next = new FailureCounter(1, nowMs, 0L);
            }
            // 窗口内累计
            else {
                int newFailures = cur.failures + 1;
                long lockedUntil = (newFailures >= props.getMaxFailures())
                        ? nowMs + lockMs
                        : cur.lockedUntilMillis;
                next = new FailureCounter(newFailures, cur.firstFailureAtMillis, lockedUntil);
            }
            if (ref.compareAndSet(cur, next)) {
                if (next.lockedUntilMillis > nowMs) {
                    return (int) Math.max(1L, ((next.lockedUntilMillis - nowMs) + 999) / 1000);
                }
                return 0;
            }
        }
    }

    /** 登录成功 → 清零计数与锁定。 */
    public void recordSuccess(String account) {
        if (account == null || account.isBlank()) return;
        counters.invalidate(account);
    }

    private long nowMs() {
        return ticker.read() / 1_000_000L;
    }

    private record FailureCounter(int failures, long firstFailureAtMillis, long lockedUntilMillis) {
    }
}
