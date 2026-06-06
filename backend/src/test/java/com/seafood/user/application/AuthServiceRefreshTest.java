package com.seafood.user.application;

import com.seafood.shared.error.DomainException;
import com.seafood.shared.security.JwtProperties;
import com.seafood.shared.security.JwtTokenProvider;
import com.seafood.shared.security.RefreshTokenRecord;
import com.seafood.shared.security.RefreshTokenStore;
import com.seafood.shared.security.Role;
import com.seafood.user.api.dto.TokenResponse;
import com.seafood.user.infra.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Sprint 2 — refresh-token 单次消费 + family 撤销路径(spec auth §Token reuse detection)。
 *
 * <p>修复 PR review 发现的 critical bug:{@code AuthService.revokeFamily} 原实现两次调用
 * {@code findByFamilyId},在第一份 list 上 {@code setConsumed(true)} 后却 {@code saveAll}
 * 第二份(fresh)list,导致 family 永远不会被撤销。该 bug 此前 0 测试覆盖。
 *
 * <p>本类专门锁定该路径,捕获 {@code refreshStore.saveAll(...)} 收到的 list 并断言
 * 所有记录的 {@code consumed} 都已被翻为 {@code true}。
 *
 * <p>Happy-path refresh 不在本类覆盖范围 — refresh token 不带 role claim,而
 * {@code AuthService.refresh()} 试图 {@code Role.valueOf(claims.get("role", String.class))}
 * 会 NPE(参见 task: "FIX: AuthService.refresh() NPE — refresh token has no role claim")。
 */
class AuthServiceRefreshTest {

    private JwtTokenProvider tokens;
    private RefreshTokenStore refreshStore;
    private UserRepository users;
    private WechatCodeExchanger wechat;
    private LoginAttemptService attempts;
    private AuthService auth;

    @BeforeEach
    void setUp() {
        JwtProperties p = new JwtProperties();
        p.setSecret("user-secret-at-least-32-bytes-long-123");
        p.setAdminSecret("admin-secret-at-least-32-bytes-4567");
        p.setAccessTokenTtl(Duration.ofMinutes(15));
        p.setRefreshTokenTtl(Duration.ofDays(1));
        tokens = new JwtTokenProvider(p);
        tokens.init();

        refreshStore = mock(RefreshTokenStore.class);
        users = mock(UserRepository.class);
        wechat = mock(WechatCodeExchanger.class);
        attempts = mock(LoginAttemptService.class);
        when(attempts.isLocked(anyString())).thenReturn(0);

        auth = new AuthService(tokens, refreshStore, users, wechat, attempts);
    }

    @Test
    void refresh_withReusedToken_revokesEntireFamilyAndThrowsTokenReused() {
        // 准备:一个被消费过的 refresh token 记录
        String familyId = "fam-" + UUID.randomUUID();
        String userId = "user-42";

        JwtTokenProvider.IssuedToken refreshToken = tokens.issueRefreshToken(userId, Role.CUSTOMER);
        // 用真实签发的 refresh token,但 store 里返回一个 consumed=true 的记录(模拟重用)
        RefreshTokenRecord reused = new RefreshTokenRecord();
        reused.setJti(refreshToken.jti());
        reused.setUserId(userId);
        reused.setFamilyId(familyId);
        reused.setAudience(AuthService.Audience.USER.name());
        reused.setConsumed(true);  // ← 已消费 → 触发 family 撤销分支
        reused.setExpiresAt(refreshToken.expiresAt());

        when(refreshStore.findByJti(refreshToken.jti())).thenReturn(Optional.of(reused));

        // family 内还有 2 个未消费的 sibling refresh,模拟同一登录的多个 refresh
        RefreshTokenRecord sibling1 = makeRecord("sib-1", userId, familyId, false);
        RefreshTokenRecord sibling2 = makeRecord("sib-2", userId, familyId, false);
        List<RefreshTokenRecord> familyMembers = List.of(reused, sibling1, sibling2);
        when(refreshStore.findByFamilyId(familyId)).thenReturn(familyMembers);

        // act + assert:抛 TOKEN_REUSED
        assertThatThrownBy(() -> auth.refresh(refreshToken.token(), AuthService.Audience.USER))
                .isInstanceOf(DomainException.class)
                .hasMessageContaining("TOKEN_REUSED");

        // 关键断言:saveAll 真的收到了 consumed=true 的 list
        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<RefreshTokenRecord>> captor = ArgumentCaptor.forClass(List.class);
        verify(refreshStore, times(1)).saveAll(captor.capture());

        List<RefreshTokenRecord> saved = captor.getValue();
        assertThat(saved)
                .as("family 内所有 record 都应被持久化为 consumed=true(防 family 不撤销 bug 回归)")
                .hasSize(3)
                .allSatisfy(r -> assertThat(r.isConsumed())
                        .as("record jti=%s 应已被标记 consumed", r.getJti())
                        .isTrue());

        // findByFamilyId 应当只被调用一次 —— 原 bug 是调用两次然后丢弃第一次的 mutate
        verify(refreshStore, times(1)).findByFamilyId(familyId);
    }

    private static RefreshTokenRecord makeRecord(String jti, String userId, String familyId, boolean consumed) {
        RefreshTokenRecord r = new RefreshTokenRecord();
        r.setJti(jti);
        r.setUserId(userId);
        r.setFamilyId(familyId);
        r.setAudience(AuthService.Audience.USER.name());
        r.setConsumed(consumed);
        r.setExpiresAt(Instant.now().plus(Duration.ofHours(1)));
        return r;
    }

    /**
     * PR review #30 回归保护 — happy-path refresh:
     * refresh token 必须带 {@code role} claim,否则 {@code AuthService.refresh()}
     * 第 158 行 {@code Role.valueOf(claims.get("role", String.class))} 会 NPE。
     *
     * <p>本测试覆盖完整成功路径:签 refresh → 解析 → 标记 consumed → 签新对。
     * 如果 {@link JwtTokenProvider#issueRefreshToken} 退化为不写 role claim,
     * 这里会因 NPE 而失败。
     */
    @Test
    void refresh_happyPath_issuesNewPairWithoutNpe() {
        String familyId = "fam-" + UUID.randomUUID();
        String userId = "user-42";
        JwtTokenProvider.IssuedToken refreshToken = tokens.issueRefreshToken(userId, Role.CUSTOMER);

        RefreshTokenRecord rec = new RefreshTokenRecord();
        rec.setJti(refreshToken.jti());
        rec.setUserId(userId);
        rec.setFamilyId(familyId);
        rec.setAudience(AuthService.Audience.USER.name());
        rec.setConsumed(false);
        rec.setExpiresAt(refreshToken.expiresAt());

        when(refreshStore.findByJti(refreshToken.jti())).thenReturn(Optional.of(rec));

        TokenResponse resp = auth.refresh(refreshToken.token(), AuthService.Audience.USER);

        // 关键:happy path 必须成功(不 NPE)
        assertThat(resp).isNotNull();
        assertThat(resp.accessToken()).isNotBlank();
        assertThat(resp.refreshToken()).isNotBlank();
        assertThat(resp.role()).isEqualTo("CUSTOMER");
        // 原 refresh token 必须被标记为 consumed
        verify(refreshStore).save(argThat(r -> r.getJti().equals(refreshToken.jti()) && r.isConsumed()));
    }

    /**
     * PR review C3 回归保护:旧 refresh token(本 PR 之前签发)没有 role claim,
     * 部署后客户端调 refresh 应当拿到 401 + TOKEN_INVALID,而不是 NPE → 500。
     *
     * <p>无法直接用 JwtTokenProvider 签"无 role" token(其方法签名强制 role);
     * 改为 mock Claims:返回 jti/sub/type 三个 claim,但 role 返回 null。
     */
    @Test
    void refresh_legacyTokenWithoutRoleClaim_throwsTokenInvalidNotNpe() {
        String familyId = "fam-" + UUID.randomUUID();
        String userId = "user-1";
        JwtTokenProvider.IssuedToken refreshToken = tokens.issueRefreshToken(userId, Role.CUSTOMER);

        RefreshTokenRecord rec = new RefreshTokenRecord();
        rec.setJti(refreshToken.jti());
        rec.setUserId(userId);
        rec.setFamilyId(familyId);
        rec.setAudience(AuthService.Audience.USER.name());
        rec.setConsumed(false);
        rec.setExpiresAt(refreshToken.expiresAt());
        when(refreshStore.findByJti(refreshToken.jti())).thenReturn(Optional.of(rec));

        // 用 spy 替换 JwtTokenProvider.parseUser,返回没有 role claim 的 Claims
        // (模拟"本 PR 之前签的" refresh token)。
        JwtTokenProvider legacyTokens = org.mockito.Mockito.mock(JwtTokenProvider.class);
        AuthService authWithLegacy = new AuthService(legacyTokens, refreshStore, users, wechat, attempts);
        io.jsonwebtoken.Jws<io.jsonwebtoken.Claims> jws = org.mockito.Mockito.mock(io.jsonwebtoken.Jws.class);
        io.jsonwebtoken.Claims legacyClaims = io.jsonwebtoken.Jwts.claims()
                .id(refreshToken.jti())
                .subject(userId)
                .add("type", "refresh")
                .build();   // 注意:无 role claim
        when(jws.getPayload()).thenReturn(legacyClaims);
        when(legacyTokens.parseUser(refreshToken.token())).thenReturn(legacyClaims);

        // 关键:应当抛 DomainException(TOKEN_INVALID),不是 NPE
        assertThatThrownBy(() -> authWithLegacy.refresh(refreshToken.token(), AuthService.Audience.USER))
                .isInstanceOf(DomainException.class)
                .hasMessageContaining("TOKEN_INVALID");
    }
}

