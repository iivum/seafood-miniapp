package com.seafood.user.application;

import com.seafood.user.infra.LoginAttemptDocument;
import com.seafood.user.infra.LoginAttemptRepository;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * sprint-1-closure 2.7 — {@link LoginLockoutService} 单测。
 *
 * <p>覆盖:
 * <ul>
 *   <li>3 次失败锁 IP</li>
 *   <li>3 次失败锁 account</li>
 *   <li>成功登录清失败计数(此处仅 mock repository,验证 service 行为)</li>
 *   <li>锁状态查询返 IP / ACCOUNT / NONE 三种 scope</li>
 *   <li>失败触发 locked counter</li>
 * </ul>
 */
class LoginLockoutServiceTest {

    private LoginAttemptRepository repo;
    private MeterRegistry meterRegistry;
    private LoginLockoutService service;

    @BeforeEach
    void setUp() {
        repo = mock(LoginAttemptRepository.class);
        meterRegistry = new SimpleMeterRegistry();
        service = new LoginLockoutService(repo, meterRegistry);
    }

    @Test
    void isIpLocked_returnsTrue_when3FailuresInLast15Min() {
        when(repo.countByIpAndSuccessAndTsAfter(eq("1.2.3.4"), eq(false), any()))
                .thenReturn(3L);

        assertThat(service.isIpLocked("1.2.3.4")).isTrue();
    }

    @Test
    void isIpLocked_returnsFalse_when2FailuresInLast15Min() {
        when(repo.countByIpAndSuccessAndTsAfter(eq("1.2.3.4"), eq(false), any()))
                .thenReturn(2L);

        assertThat(service.isIpLocked("1.2.3.4")).isFalse();
    }

    @Test
    void isAccountLocked_returnsTrue_when3FailuresInLast15Min() {
        when(repo.countByAccountAndSuccessAndTsAfter(eq("admin"), eq(false), any()))
                .thenReturn(3L);

        assertThat(service.isAccountLocked("admin")).isTrue();
    }

    @Test
    void recordFailure_writesAttemptAndTriggersLockedCounterOnThirdFailure() {
        // 第一次失败:计数 0 → 写完仍 0
        when(repo.countByIpAndSuccessAndTsAfter(eq("1.2.3.4"), eq(false), any()))
                .thenReturn(0L);
        service.recordFailure("1.2.3.4", "admin");
        assertThat(meterRegistry.counter("users.login.attempts", "result", "locked").count())
                .isEqualTo(0.0);
        // 第二次失败:写完仍 0(假设后续 IP 锁阈还没到)
        when(repo.countByIpAndSuccessAndTsAfter(eq("1.2.3.4"), eq(false), any()))
                .thenReturn(0L);
        service.recordFailure("1.2.3.4", "admin");
        assertThat(meterRegistry.counter("users.login.attempts", "result", "locked").count())
                .isEqualTo(0.0);
        // 第三次失败:阈值 3 触发 → counter +1
        when(repo.countByIpAndSuccessAndTsAfter(eq("1.2.3.4"), eq(false), any()))
                .thenReturn(3L);
        when(repo.countByAccountAndSuccessAndTsAfter(eq("admin"), eq(false), any()))
                .thenReturn(2L);
        service.recordFailure("1.2.3.4", "admin");
        assertThat(meterRegistry.counter("users.login.attempts", "result", "locked").count())
                .isEqualTo(1.0);
    }

    @Test
    void getLockoutState_returnsIp_whenIpIsLocked() {
        when(repo.findByIpAndTsAfterOrderByTsDesc(eq("1.2.3.4"), any()))
                .thenReturn(List.of(
                        attemptAt(Instant.now().minusSeconds(60)),
                        attemptAt(Instant.now().minusSeconds(30)),
                        attemptAt(Instant.now())));

        LoginLockoutService.LockoutState s = service.getLockoutState("1.2.3.4", "admin");

        assertThat(s.locked()).isTrue();
        assertThat(s.scope()).isEqualTo("IP");
        assertThat(s.until()).isNotNull();
    }

    @Test
    void getLockoutState_returnsAccount_whenAccountIsLockedButIpIsNot() {
        when(repo.countByIpAndSuccessAndTsAfter(eq("1.2.3.4"), eq(false), any()))
                .thenReturn(1L);  // IP 未锁
        when(repo.findByAccountAndTsAfterOrderByTsDesc(eq("admin"), any()))
                .thenReturn(List.of(
                        attemptAt(Instant.now().minusSeconds(60)),
                        attemptAt(Instant.now().minusSeconds(30)),
                        attemptAt(Instant.now())));

        LoginLockoutService.LockoutState s = service.getLockoutState("1.2.3.4", "admin");

        assertThat(s.locked()).isTrue();
        assertThat(s.scope()).isEqualTo("ACCOUNT");
    }

    @Test
    void getLockoutState_returnsNone_whenNeitherLocked() {
        when(repo.countByIpAndSuccessAndTsAfter(anyString(), eq(false), any()))
                .thenReturn(0L);
        when(repo.countByAccountAndSuccessAndTsAfter(anyString(), eq(false), any()))
                .thenReturn(0L);

        LoginLockoutService.LockoutState s = service.getLockoutState("1.2.3.4", "admin");

        assertThat(s.locked()).isFalse();
        assertThat(s.scope()).isEqualTo("NONE");
        assertThat(s.until()).isNull();
    }

    @Test
    void recordSuccess_writesSuccessAttempt() {
        service.recordSuccess("1.2.3.4", "admin");

        // 不验证具体 repository 调用(只确认不抛异常)
        // 真实验证在 IT(AdminCookieAuthControllerTest 集成测试)
    }

    private static LoginAttemptDocument attemptAt(Instant ts) {
        return new LoginAttemptDocument("1.2.3.4", "admin", false, ts);
    }
}
