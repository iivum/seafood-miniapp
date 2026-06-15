package com.seafood.user.api;

import com.seafood.shared.security.JwtProperties;
import com.seafood.shared.security.JwtTokenProvider;
import com.seafood.shared.security.Role;
import com.seafood.user.api.dto.AdminLoginRequest;
import com.seafood.user.api.dto.TokenResponse;
import com.seafood.user.application.AuthService;
import com.seafood.user.application.TokenRevocationService;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 路线图 2.12:AdminCookieAuthController 三端点契约。
 *
 * <p>三组断言(plain JUnit + Mockito,与 {@link AuthControllerLogoutTest} 同款):
 * <ol>
 *   <li>cookieLogin:204 + Set-Cookie 头存在 + 走 AuthService.adminLogin</li>
 *   <li>logout:204 + 读 cookie → revoke jti;无 cookie 也 204(幂等)</li>
 *   <li>csrf:200 + 32 hex 字符 token</li>
 * </ol>
 */
class AdminCookieAuthControllerTest {

    private final AuthService auth = mock(AuthService.class);
    private final TokenRevocationService revocations = mock(TokenRevocationService.class);
    private JwtTokenProvider tokens;
    private AdminCookieAuthController controller;

    @org.junit.jupiter.api.BeforeEach
    void setUp() {
        JwtProperties p = new JwtProperties();
        p.setSecret("user-secret-at-least-32-bytes-long-123");
        p.setAdminSecret("admin-secret-at-least-32-bytes-4567");
        p.setAccessTokenTtl(java.time.Duration.ofMinutes(15));
        p.setRefreshTokenTtl(java.time.Duration.ofDays(1));
        tokens = new JwtTokenProvider(p);
        tokens.init();
        controller = new AdminCookieAuthController(auth, revocations, tokens);
    }

    @Test
    void cookieLogin_setsCookieAndReturns204() {
        AdminLoginRequest req = new AdminLoginRequest("admin", "secret");
        JwtTokenProvider.IssuedToken access = tokens.issueAdminAccessToken("admin-bootstrap", Role.ADMIN);
        when(auth.adminLogin(req, "127.0.0.1")).thenReturn(
                new TokenResponse(access.token(), "refresh-stub", Instant.now(), Instant.now(), "ADMIN"));

        MockHttpServletRequest httpReq = new MockHttpServletRequest("POST", "/api/admin/auth/cookie-login");
        httpReq.setRemoteAddr("127.0.0.1");
        MockHttpServletResponse httpRes = new MockHttpServletResponse();

        ResponseEntity<Void> resp = controller.cookieLogin(req, httpReq, httpRes);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        String setCookie = httpRes.getHeader(HttpHeaders.SET_COOKIE);
        assertThat(setCookie).isNotNull();
        assertThat(setCookie).contains("seafood_admin_token=");
        assertThat(setCookie).contains("HttpOnly");
        assertThat(setCookie).contains("Path=/");
        assertThat(setCookie).contains("Max-Age=900");
        assertThat(setCookie).contains("SameSite=Lax");
    }

    @Test
    void logout_withValidCookie_invokesRevokeAndClearsCookie() {
        JwtTokenProvider.IssuedToken issued = tokens.issueAdminAccessToken("admin-bootstrap", Role.ADMIN);

        MockHttpServletRequest httpReq = new MockHttpServletRequest("POST", "/api/admin/auth/logout");
        httpReq.setCookies(new jakarta.servlet.http.Cookie("seafood_admin_token", issued.token()));
        MockHttpServletResponse httpRes = new MockHttpServletResponse();

        ResponseEntity<Void> resp = controller.logout(httpReq, httpRes);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        verify(revocations).revoke(eq(issued.jti()), eq("admin-bootstrap"), any(Instant.class));
        String setCookie = httpRes.getHeader(HttpHeaders.SET_COOKIE);
        assertThat(setCookie).contains("Max-Age=0");
    }

    @Test
    void logout_withoutCookie_isNoopAndReturns204() {
        MockHttpServletRequest httpReq = new MockHttpServletRequest("POST", "/api/admin/auth/logout");
        MockHttpServletResponse httpRes = new MockHttpServletResponse();

        ResponseEntity<Void> resp = controller.logout(httpReq, httpRes);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        verify(revocations, never()).revoke(any(), any(), any());
        // 仍清 cookie(防御性 — 浏览器可能残留)
        assertThat(httpRes.getHeader(HttpHeaders.SET_COOKIE)).contains("Max-Age=0");
    }

    @Test
    void logout_withInvalidCookieToken_stillReturns204() {
        MockHttpServletRequest httpReq = new MockHttpServletRequest("POST", "/api/admin/auth/logout");
        httpReq.setCookies(new jakarta.servlet.http.Cookie("seafood_admin_token", "garbage-not-a-jwt"));
        MockHttpServletResponse httpRes = new MockHttpServletResponse();

        ResponseEntity<Void> resp = controller.logout(httpReq, httpRes);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        verify(revocations, never()).revoke(any(), any(), any());
    }

    @Test
    void csrf_returns32HexChars() {
        ResponseEntity<java.util.Map<String, String>> resp = controller.csrf();
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        String csrfToken = resp.getBody().get("csrfToken");
        assertThat(csrfToken).isNotNull();
        assertThat(csrfToken).hasSize(32);
        assertThat(csrfToken).matches("[0-9a-f]{32}");
    }
}
