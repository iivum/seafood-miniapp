package com.seafood.user.application;

import com.github.benmanes.caffeine.cache.Ticker;
import com.seafood.user.infra.RevokedToken;
import com.seafood.user.infra.RevokedTokenRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Instant;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Sprint 2 §3.4 — {@link TokenRevocationService} 单元测试。
 *
 * <p>覆盖:
 * <ul>
 *   <li>普通 revoke 写库 + 写正缓存</li>
 *   <li>isRevoked 缓存命中路径(0 次 MongoDB)</li>
 *   <li>isRevoked 缓存未命中 → 查 Mongo → 写负缓存</li>
 *   <li>force-logout:写 sentinel 后 isRevoked(任意 jti, userId) 都返 true</li>
 *   <li>负缓存 5s 后失效(快进 ticker 验证)</li>
 *   <li>已过期的 revoke 直接被忽略(不写库)</li>
 *   <li>null jti 直接返回 false</li>
 * </ul>
 */
class TokenRevocationServiceTest {

    private RevokedTokenRepository repo;
    private FakeTicker ticker;
    private TokenRevocationService svc;

    @BeforeEach
    void setUp() {
        repo = mock(RevokedTokenRepository.class);
        // 默认 existsById = false, save 啥都不做
        when(repo.existsById(any())).thenReturn(false);
        ticker = new FakeTicker();
        svc = new TokenRevocationService(repo, ticker);
    }

    @Test
    void revoke_writesDocAndPopsPositiveCache() {
        Instant exp = Instant.now().plusSeconds(3600);
        svc.revoke("jti-1", "user-1", exp);

        ArgumentCaptor<RevokedToken> cap = ArgumentCaptor.forClass(RevokedToken.class);
        verify(repo).save(cap.capture());
        assertThat(cap.getValue().getJti()).isEqualTo("jti-1");
        assertThat(cap.getValue().getUserId()).isEqualTo("user-1");
        assertThat(cap.getValue().getExpiresAt()).isEqualTo(exp);

        // 缓存也应有(避免后续 isRevoked 再走 Mongo)
        assertThat(svc.isRevoked("jti-1", "user-1")).isTrue();
        // 同一个 isRevoked 应直接命中正缓存,不再查 Mongo
        assertThat(svc.isRevoked("jti-1", "user-1")).isTrue();
        verify(repo, never()).existsById("jti-1");
    }

    @Test
    void isRevoked_returnsFalseForUnknownJti() {
        assertThat(svc.isRevoked("unknown", "user-1")).isFalse();
        verify(repo).existsById("unknown");
    }

    @Test
    void isRevoked_returnsTrueWhenDocumentExistsInMongo() {
        when(repo.existsById("present")).thenReturn(true);
        assertThat(svc.isRevoked("present", "user-1")).isTrue();
        // 第二次调用:命中正缓存,不再查 Mongo
        assertThat(svc.isRevoked("present", "user-1")).isTrue();
        verify(repo, times(1)).existsById("present");
    }

    @Test
    void forceLogout_makesAllJtisForUserRevoked() {
        svc.revokeAllForUser("user-X");

        // sentinel 文档应当写库
        ArgumentCaptor<RevokedToken> cap = ArgumentCaptor.forClass(RevokedToken.class);
        verify(repo).save(cap.capture());
        RevokedToken saved = cap.getValue();
        assertThat(saved.getJti()).isEqualTo("u:user-X");
        assertThat(saved.getUserId()).isEqualTo("user-X");

        // mock sentinel existsById = true
        when(repo.existsById("u:user-X")).thenReturn(true);

        // 任意 jti + 同一 userId → true
        assertThat(svc.isRevoked("jti-A", "user-X")).isTrue();
        assertThat(svc.isRevoked("jti-B", "user-X")).isTrue();

        // 其它 user → 仍然 false(用未缓存过的 jti 验证 sentinel 隔离)
        when(repo.existsById("u:user-Y")).thenReturn(false);
        assertThat(svc.isRevoked("jti-C", "user-Y")).isFalse();
    }

    @Test
    void isRevoked_negativeCacheExpiresAfterFiveSeconds() {
        // 第一次:查 Mongo,写负缓存
        assertThat(svc.isRevoked("cool", "user-1")).isFalse();
        verify(repo, times(1)).existsById("cool");

        // 5s 内:不查 Mongo
        ticker.advance(4_999);
        assertThat(svc.isRevoked("cool", "user-1")).isFalse();
        verify(repo, times(1)).existsById("cool");

        // 5s 之后:负缓存失效,重新查 Mongo
        ticker.advance(2); // 现在过 5.001s
        assertThat(svc.isRevoked("cool", "user-1")).isFalse();
        verify(repo, times(2)).existsById("cool");
    }

    @Test
    void revoke_expiredTokenIsSkipped() {
        Instant past = Instant.now().minusSeconds(60);
        svc.revoke("expired", "user-1", past);
        // 不写库;不污染缓存
        verify(repo, never()).save(any());
        assertThat(svc.isRevoked("expired", "user-1")).isFalse();
    }

    @Test
    void isRevoked_nullJtiReturnsFalse() {
        assertThat(svc.isRevoked(null, "user-1")).isFalse();
        assertThat(svc.isRevoked("", "user-1")).isFalse();
        verify(repo, never()).existsById(any());
    }

    @Test
    void invalidateCache_clearsLocalCache() {
        when(repo.existsById("z")).thenReturn(true);
        assertThat(svc.isRevoked("z", "u")).isTrue();
        svc.invalidateCache();
        // 缓存被清,会再查一次 Mongo
        assertThat(svc.isRevoked("z", "u")).isTrue();
        verify(repo, times(2)).existsById("z");
    }

    /**
     * PR review #22 + I10 回归保护:isRevoked 与 revoke 并发时,TRUE 永远赢。
     *
     * <p>原 bug 场景:
     * <pre>
     *   T1 isRevoked("X") → cache miss → existsById("X") = false
     *   T2 revoke("X", ...) → save + cache.put("X", TRUE)
     *   T1                  → cache.put("X", FALSE) ← 覆盖 TRUE!
     *   后续 5s 内 isRevoked("X") 都返 false (错)
     * </pre>
     *
     * <p>fix:isRevoked 用 {@code putIfAbsent} 写 FALSE,并发的 TRUE 不会被覆盖。
     *
     * <p>PR review I10:真正并发(原版用 sequential mock,不能真触发 race)。
     * N 个线程同时调 isRevoked("X", "u") + 1 个线程调 revoke("X", ...) —
     * 任何"看到 FALSE"的结果都说明 fix 失效。
     */
    @Test
    void concurrentRevokeAfterIsRevokedDbReadDoesNotGetOverwrittenByFalse() throws Exception {
        // 所有 isRevoked 调用都先返 false(模拟 DB 没记录)
        when(repo.existsById("X")).thenReturn(false);

        int threadCount = 50;
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(threadCount + 1);
        ConcurrentLinkedQueue<Boolean> results = new ConcurrentLinkedQueue<>();
        ExecutorService pool = Executors.newFixedThreadPool(threadCount + 1);
        try {
            // N 个并发 isRevoked
            for (int i = 0; i < threadCount; i++) {
                pool.submit(() -> {
                    try {
                        start.await();
                        results.add(svc.isRevoked("X", "u"));
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    } finally {
                        done.countDown();
                    }
                });
            }
            // 1 个并发 revoke
            pool.submit(() -> {
                try {
                    start.await();
                    when(repo.existsById("X")).thenReturn(true);
                    svc.revoke("X", "u", Instant.now().plusSeconds(60));
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    done.countDown();
                }
            });
            start.countDown();
            assertThat(done.await(10, TimeUnit.SECONDS))
                    .as("all %d workers should complete within 10s", threadCount + 1)
                    .isTrue();
        } finally {
            pool.shutdownNow();
        }

        // 关键:并发运行后,再次 isRevoked 必须是 TRUE(revoke 已经写入)
        assertThat(svc.isRevoked("X", "u"))
                .as("post-concurrent-revoke isRevoked must return TRUE")
                .isTrue();
        // 至少有部分线程看到 TRUE(写入的瞬间);没有全部是 FALSE(那说明 FALSE 覆盖了 TRUE)
        long trueCount = results.stream().filter(Boolean.TRUE::equals).count();
        assertThat(trueCount)
                .as("at least some concurrent callers should observe TRUE after revoke completes")
                .isGreaterThan(0);
    }

    /**
     * PR review #22 强化:即使中间发生多次并发写入,TRUE 仍是 dominant state。
     */
    @Test
    void putIfAbsentKeepsTrueAcrossMultipleConcurrentWriters() {
        // 第一次 isRevoked:写负缓存
        assertThat(svc.isRevoked("Y", "u")).isFalse();
        // 此时 cache 里有 FALSE

        // 撤销发生:写入 TRUE(应该无条件覆盖)
        when(repo.existsById("Y")).thenReturn(true);
        svc.revoke("Y", "u", Instant.now().plusSeconds(60));

        // 后续 isRevoked:命中正缓存,直接 true
        assertThat(svc.isRevoked("Y", "u")).isTrue();
        // repo.existsById 仍只被调 1 次(在第一次 isRevoked 时)
        verify(repo, times(1)).existsById("Y");
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
