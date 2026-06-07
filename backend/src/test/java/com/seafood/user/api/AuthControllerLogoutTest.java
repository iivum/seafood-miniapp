package com.seafood.user.api;

import com.seafood.shared.security.JwtProperties;
import com.seafood.shared.security.JwtTokenProvider;
import com.seafood.shared.security.Role;
import com.seafood.shared.security.UserPrincipal;
import com.seafood.user.application.AuthService;
import com.seafood.user.application.TokenRevocationService;
import io.jsonwebtoken.Claims;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Sprint 2 §3.6 — {@code POST /api/auth/logout} 调 {@link TokenRevocationService#revoke}。
 *
 * <p>本测试用 plain JUnit + Mockito 直接构造 controller(不用 Spring MVC),验证:
 * <ul>
 *   <li>有 token + 有 principal → 调 revoke(jti, userId, exp),返 204</li>
 *   <li>无 token → 返 401,不动 revocation</li>
 *   <li>空 token → 返 401</li>
 *   <li>token 解析失败(已过期)→ 仍 204,revocation 静默</li>
 * </ul>
 */
class AuthControllerLogoutTest {

    private final AuthService auth = mock(AuthService.class);
    private final TokenRevocationService revocations = mock(TokenRevocationService.class);
    private JwtTokenProvider tokens;
    private AuthController controller;

    @org.junit.jupiter.api.BeforeEach
    void setUp() {
        JwtProperties p = new JwtProperties();
        p.setSecret("user-secret-at-least-32-bytes-long-123");
        p.setAdminSecret("admin-secret-at-least-32-bytes-4567");
        p.setAccessTokenTtl(java.time.Duration.ofMinutes(15));
        p.setRefreshTokenTtl(java.time.Duration.ofDays(1));
        tokens = new JwtTokenProvider(p);
        tokens.init();
        controller = new AuthController(auth, tokens, revocations);
    }

    @org.junit.jupiter.api.AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void logout_withValidTokenAndPrincipal_invokesRevokeAndReturns204() {
        JwtTokenProvider.IssuedToken issued = tokens.issueAccessToken("user-42", Role.CUSTOMER);
        UserPrincipal principal = new UserPrincipal("user-42", Role.CUSTOMER);
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities()));

        MockHttpServletRequest req = new MockHttpServletRequest("POST", "/api/auth/logout");
        req.addHeader("Authorization", "Bearer " + issued.token());

        ResponseEntity<Void> resp = controller.logout(req, principal);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        verify(revocations).revoke(eq(issued.jti()), eq("user-42"), any(Instant.class));
    }

    @Test
    void logout_withoutAuthorizationHeader_returns401() {
        MockHttpServletRequest req = new MockHttpServletRequest("POST", "/api/auth/logout");
        ResponseEntity<Void> resp = controller.logout(req, null);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        verify(revocations, never()).revoke(any(), any(), any());
    }

    @Test
    void logout_withEmptyBearerToken_returns401() {
        MockHttpServletRequest req = new MockHttpServletRequest("POST", "/api/auth/logout");
        req.addHeader("Authorization", "Bearer ");
        ResponseEntity<Void> resp = controller.logout(req, null);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        verify(revocations, never()).revoke(any(), any(), any());
    }

    @Test
    void logout_withExpiredToken_stillReturns204() {
        // PR review #28:签一个<em>出生即过期</em>的 token(TTL 用负 duration,让 exp 字段落在过去),
        // 而不是用 Thread.sleep 等自然过期 — 后者在慢 CI / 高负载下会 flake(50ms 不够)。
        JwtProperties p = new JwtProperties();
        p.setSecret("user-secret-at-least-32-bytes-long-123");
        p.setAdminSecret("admin-secret-at-least-32-bytes-4567");
        p.setAccessTokenTtl(java.time.Duration.ofSeconds(-1));
        p.setRefreshTokenTtl(java.time.Duration.ofDays(1));
        JwtTokenProvider expiredOnIssue = new JwtTokenProvider(p);
        expiredOnIssue.init();
        JwtTokenProvider.IssuedToken issued = expiredOnIssue.issueAccessToken("user-1", Role.CUSTOMER);

        MockHttpServletRequest req = new MockHttpServletRequest("POST", "/api/auth/logout");
        req.addHeader("Authorization", "Bearer " + issued.token());
        ResponseEntity<Void> resp = controller.logout(req, null);

        // 过期 token:解析抛 JwtException,catch 静默,仍 204
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        verify(revocations, never()).revoke(any(), any(), any());
    }
}
