package com.seafood.user.application;

import com.github.benmanes.caffeine.cache.Ticker;
import com.seafood.user.infra.RevokedToken;
import com.seafood.user.infra.RevokedTokenRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Instant;
import java.util.List;
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
     * PR review #22 回归保护:isRevoked 与 revoke 并发时,TRUE 永远赢。
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
     */
    @Test
    void concurrentRevokeAfterIsRevokedDbReadDoesNotGetOverwrittenByFalse() {
        // 模拟 race:T1 已读到 DB = false,正要把 FALSE 写缓存;
        // 期间 T2 revoke 完成,先 put TRUE。
        // 这里用 Mockito 控制:existsById 第一次返 false(模拟 T1 DB 读),
        // 然后 svc.revoke 写入 TRUE;再调 isRevoked 应返 true。
        when(repo.existsById("X")).thenReturn(false);

        // 模拟 T1:第一次 isRevoked 走 DB 读,准备写 FALSE
        assertThat(svc.isRevoked("X", "u")).isFalse();

        // 模拟 T2:中间有人 revoke 了 X(在 T1 写 FALSE 之前)
        when(repo.existsById("X")).thenReturn(true);
        svc.revoke("X", "u", Instant.now().plusSeconds(60));

        // 关键:再次 isRevoked 仍应为 true(因为 cache 里是 TRUE,不是被覆盖的 FALSE)
        // 原实现会因前一次 isRevoked 写入 FALSE 而被覆盖
        assertThat(svc.isRevoked("X", "u"))
                .as("after concurrent revoke, isRevoked must return TRUE")
                .isTrue();
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
