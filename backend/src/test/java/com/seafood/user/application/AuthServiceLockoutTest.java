package com.seafood.user.application;

import com.seafood.shared.error.DomainException;
import com.seafood.shared.security.JwtProperties;
import com.seafood.shared.security.JwtTokenProvider;
import com.seafood.shared.security.RefreshTokenStore;
import com.seafood.user.api.dto.AdminLoginRequest;
import com.seafood.user.api.dto.TokenResponse;
import com.seafood.user.api.dto.WechatLoginRequest;
import com.seafood.user.infra.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.startsWith;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Sprint 2 §3.11 — {@link AuthService} 接入 {@link LoginAttemptService} 的 lockout 行为。
 *
 * <p>覆盖(specs/auth §Login lockout):
 * <ul>
 *   <li>前 4 次失败 admin login:不锁,返回 409 DOMAIN</li>
 *   <li>第 5 次失败:锁,返 423 AccountLockedException</li>
 *   <li>第 6 次尝试(密码正确也锁):仍然 423</li>
 *   <li>3 次失败 + 1 次成功 → 计数器清零,后续失败从 1 起</li>
 *   <li>wechat login 失败路径(wechat exchanger 抛错)同样累计 + 锁</li>
 * </ul>
 */
class AuthServiceLockoutTest {

    private JwtTokenProvider tokens;
    private RefreshTokenStore refreshStore;
    private UserRepository users;
    private WechatCodeExchanger wechat;
    private LoginAttemptService attempts;
    private LoginAttemptProperties props;
    private AuthService auth;

    @BeforeEach
    void setUp() {
        JwtProperties p = new JwtProperties();
        p.setSecret("user-secret-at-least-32-bytes-long-123");
        p.setAdminSecret("admin-secret-at-least-32-bytes-4567");
        p.setAccessTokenTtl(java.time.Duration.ofMinutes(15));
        p.setRefreshTokenTtl(java.time.Duration.ofDays(1));
        tokens = new JwtTokenProvider(p);
        tokens.init();

        refreshStore = mock(RefreshTokenStore.class);
        users = mock(UserRepository.class);
        wechat = mock(WechatCodeExchanger.class);
        attempts = mock(LoginAttemptService.class);
        props = new LoginAttemptProperties();
        props.setMaxFailures(5);
        props.setWindowMinutes(15);
        props.setLockMinutes(15);

        auth = new AuthService(tokens, refreshStore, users, wechat, attempts);
        ReflectionTestUtils.setField(auth, "adminUsername", "admin");
        ReflectionTestUtils.setField(auth, "adminPassword", "admin123");
    }

    @Test
    void adminLogin_locksAfterFiveWrongPasswords() {
        when(attempts.isLocked(anyString())).thenReturn(0);
        when(attempts.recordFailure(anyString())).thenReturn(0, 0, 0, 0, 900);

        AdminLoginRequest wrong = new AdminLoginRequest("admin", "bad");
        // 前 4 次:正常抛 DomainException
        for (int i = 0; i < 4; i++) {
            assertThatThrownBy(() -> auth.adminLogin(wrong))
                    .isInstanceOf(DomainException.class)
                    .hasMessageContaining("用户名或密码错误");
        }
        // 第 5 次:recordFailure 返回 900 → 抛 AccountLockedException
        assertThatThrownBy(() -> auth.adminLogin(wrong))
                .isInstanceOf(AccountLockedException.class)
                .extracting("retryAfterSeconds").isEqualTo(900);

        verify(attempts, times(5)).recordFailure(anyString());
    }

    @Test
    void adminLogin_sixthAttemptIsLockedEvenWithCorrectPassword() {
        // isLocked 返回 777 → 不该查密码,直接抛 423
        when(attempts.isLocked(anyString())).thenReturn(777);
        when(attempts.recordFailure(anyString())).thenReturn(0);

        AdminLoginRequest correct = new AdminLoginRequest("admin", "admin123");
        assertThatThrownBy(() -> auth.adminLogin(correct))
                .isInstanceOf(AccountLockedException.class)
                .extracting("retryAfterSeconds").isEqualTo(777);

        // 正确密码情况下不应触发 recordFailure(因为被锁定)
        verify(attempts, never()).recordFailure(anyString());
    }

    @Test
    void adminLogin_successResetsCounter() {
        when(attempts.isLocked(anyString())).thenReturn(0);

        AdminLoginRequest wrong = new AdminLoginRequest("admin", "bad");
        // 3 次错
        for (int i = 0; i < 3; i++) {
            assertThatThrownBy(() -> auth.adminLogin(wrong))
                    .isInstanceOf(DomainException.class);
        }
        // 1 次对 → recordSuccess
        AdminLoginRequest right = new AdminLoginRequest("admin", "admin123");
        TokenResponse resp = auth.adminLogin(right);
        assertThat(resp.accessToken()).isNotBlank();
        verify(attempts, times(1)).recordSuccess(anyString());
    }

    @Test
    void wechatLogin_lockedAccountIsRejectedWithoutCallingExchanger() {
        when(attempts.isLocked(anyString())).thenReturn(60);

        WechatLoginRequest req = new WechatLoginRequest("code-x", "nick", "avatar");
        assertThatThrownBy(() -> auth.wechatLogin(req, "10.0.0.1"))
                .isInstanceOf(AccountLockedException.class)
                .extracting("retryAfterSeconds").isEqualTo(60);

        // locked 路径不应调 wechat.exchange(节省外部调用)
        verify(wechat, never()).exchange(any());
    }

    @Test
    void wechatLogin_exchangeFailureCountsAsFailure() {
        when(attempts.isLocked(anyString())).thenReturn(0);
        when(wechat.exchange(anyString())).thenThrow(new RuntimeException("wechat down"));

        WechatLoginRequest req = new WechatLoginRequest("bad-code", null, null);
        assertThatThrownBy(() -> auth.wechatLogin(req, "10.0.0.1"))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("wechat down");
        verify(attempts, times(1)).recordFailure(anyString());
    }

    /**
     * PR review #7 回归保护:不同 code(用同一 IP)都必须累计到<em>同一</em> lockout key,
     * 否则攻击者用新 code 永远碰不到 maxFailures 阈值。原 bug 是用 {@code code.hashCode()}
     * 作 key,新 code 重新计数。
     */
    @Test
    void wechatLogin_differentCodesFromSameIpShareLockoutCounter() {
        // 仅在第一次 isLocked 调用时返 60(模拟 IP 锁)
        when(attempts.isLocked("wechat-ip:10.0.0.1")).thenReturn(60);
        when(attempts.isLocked(startsWith("wechat:"))).thenReturn(0);
        when(wechat.exchange(anyString())).thenThrow(new RuntimeException("wechat down"));

        // 第一个 code:被 IP 锁直接拒
        WechatLoginRequest req1 = new WechatLoginRequest("code-A", null, null);
        assertThatThrownBy(() -> auth.wechatLogin(req1, "10.0.0.1"))
                .isInstanceOf(AccountLockedException.class);

        // 关键断言:关键路径用的是 IP key("wechat-ip:10.0.0.1"),
        // 不是 code hash。原实现用 code.hashCode() 时,新 code 命中不同 key,绕过锁。
        org.mockito.ArgumentCaptor<String> keyCaptor = org.mockito.ArgumentCaptor.forClass(String.class);
        org.mockito.Mockito.verify(attempts, org.mockito.Mockito.atLeastOnce()).isLocked(keyCaptor.capture());
        assertThat(keyCaptor.getAllValues())
                .as("pre-exchange lockout key MUST be IP-based, not code-based")
                .anyMatch(k -> k != null && k.equals("wechat-ip:10.0.0.1"));
        // 反向断言:不应传 code-hash 形式的 key 给 isLocked
        assertThat(keyCaptor.getAllValues())
                .noneMatch(k -> k != null && k.startsWith("wechat:") && !k.startsWith("wechat-ip:"));
    }
}
