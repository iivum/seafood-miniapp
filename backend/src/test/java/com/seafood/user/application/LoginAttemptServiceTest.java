package com.seafood.user.application;

import com.github.benmanes.caffeine.cache.Ticker;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Sprint 2 §3.9 — {@link LoginAttemptService} 单元测试(plain JUnit + Mockito-free)。
 *
 * <p>用假 {@link Ticker} 推进时间,验证:
 * <ul>
 *   <li>5 次连续失败 → 第 5 次返回 lock 秒数(>0),account 被锁</li>
 *   <li>锁定后 isLocked 返剩余秒数</li>
 *   <li>3 次失败 + 1 次成功 → 计数器清零;后续失败再从 1 起</li>
 *   <li>窗口过期(>15min)→ 计数器重置</li>
 *   <li>锁定过期(>15min)→ 解锁;再失败从 1 起</li>
 *   <li>不同 account 独立</li>
 * </ul>
 */
class LoginAttemptServiceTest {

    private static final LoginAttemptProperties PROPS;
    static {
        PROPS = new LoginAttemptProperties();
        PROPS.setMaxFailures(5);
        PROPS.setWindowMinutes(15);
        PROPS.setLockMinutes(15);
    }

    @Test
    void fiveFailuresTriggerLock() {
        FakeTicker ticker = new FakeTicker();
        LoginAttemptService svc = new LoginAttemptService(PROPS, ticker);

        // 4 次失败:不锁
        for (int i = 0; i < 4; i++) {
            assertThat(svc.recordFailure("user1"))
                    .as("after %d failures", i + 1)
                    .isEqualTo(0);
        }
        // 第 5 次失败:返回 lock 剩余秒数(15min = 900s)
        int retryAfter = svc.recordFailure("user1");
        assertThat(retryAfter).isEqualTo(900);

        // isLocked 也应返 900 左右(因为我们没有推进时间)
        assertThat(svc.isLocked("user1")).isEqualTo(900);
    }

    @Test
    void successResetsCounter() {
        FakeTicker ticker = new FakeTicker();
        LoginAttemptService svc = new LoginAttemptService(PROPS, ticker);

        for (int i = 0; i < 3; i++) {
            svc.recordFailure("user1");
        }
        svc.recordSuccess("user1");

        // 后续失败:从 1 起,且不锁
        int retryAfter = svc.recordFailure("user1");
        assertThat(retryAfter).isEqualTo(0);
        assertThat(svc.isLocked("user1")).isEqualTo(0);
    }

    @Test
    void lockExpiresAfterLockMinutes() {
        FakeTicker ticker = new FakeTicker();
        LoginAttemptService svc = new LoginAttemptService(PROPS, ticker);

        for (int i = 0; i < 5; i++) {
            svc.recordFailure("user1");
        }
        assertThat(svc.isLocked("user1")).isGreaterThan(0);

        // 推进 14 分钟 59 秒:仍锁
        ticker.advance(14L * 60_000L + 59_000L);
        assertThat(svc.isLocked("user1")).isGreaterThan(0);

        // 推进到 15 分钟 1 秒:已解锁
        ticker.advance(2_000L);
        assertThat(svc.isLocked("user1")).isEqualTo(0);

        // 后续失败:从 1 起
        assertThat(svc.recordFailure("user1")).isEqualTo(0);
    }

    @Test
    void windowSlidesAfterWindowMinutes() {
        FakeTicker ticker = new FakeTicker();
        LoginAttemptService svc = new LoginAttemptService(PROPS, ticker);

        for (int i = 0; i < 3; i++) {
            svc.recordFailure("user1");
        }
        // 推进 15 分钟 1 秒(超过 window):旧失败已不在窗口内
        ticker.advance(15L * 60_000L + 1_000L);
        // 后续失败:从 1 起
        assertThat(svc.recordFailure("user1")).isEqualTo(0);
        assertThat(svc.isLocked("user1")).isEqualTo(0);
    }

    @Test
    void separateAccountsAreIndependent() {
        FakeTicker ticker = new FakeTicker();
        LoginAttemptService svc = new LoginAttemptService(PROPS, ticker);

        for (int i = 0; i < 5; i++) {
            svc.recordFailure("user1");
        }
        assertThat(svc.isLocked("user1")).isGreaterThan(0);
        assertThat(svc.isLocked("user2")).isEqualTo(0);
    }

    @Test
    void nullOrBlankAccountIsNoOp() {
        FakeTicker ticker = new FakeTicker();
        LoginAttemptService svc = new LoginAttemptService(PROPS, ticker);

        assertThat(svc.isLocked(null)).isEqualTo(0);
        assertThat(svc.isLocked("")).isEqualTo(0);
        assertThat(svc.recordFailure(null)).isEqualTo(0);
        assertThat(svc.recordFailure("")).isEqualTo(0);
        // recordSuccess 静默
        svc.recordSuccess(null);
    }

    /** 手动时间推进器;{@link Ticker#read()} 始终返回 0 + 偏移(纳秒)。 */
    private static final class FakeTicker implements Ticker {
        private final AtomicReference<Long> elapsedNanos = new AtomicReference<>(0L);

        @Override
        public long read() {
            return elapsedNanos.get();
        }

        void advance(long millis) {
            elapsedNanos.updateAndGet(v -> v + millis * 1_000_000L);
        }
    }
}
