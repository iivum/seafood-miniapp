package com.seafood.shared.security;

import com.seafood.user.application.TokenRevocationService;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;
import tools.jackson.databind.json.JsonMapper;

import java.time.Instant;
import java.util.Date;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Sprint 2 §3.5 — {@link JwtAuthenticationFilter} 在 token 解析后追加撤销检查。
 *
 * <p>覆盖:
 * <ul>
 *   <li>已撤销 jti → filter 写 401 + {@code code=TOKEN_REVOKED} body,不下游</li>
 *   <li>未撤销 jti → 正常走 FilterChain,SecurityContext 拿到 principal</li>
 *   <li>无效 token → 仍 clearContext + 放行(原行为不变)</li>
 *   <li>无 Authorization header → 放行(原行为不变)</li>
 * </ul>
 */
class JwtAuthenticationFilterRevocationTest {

    private final tools.jackson.databind.ObjectMapper mapper = JsonMapper.builder().build();
    private JwtTokenProvider tokens;
    private TokenRevocationService revocations;
    private JwtAuthenticationFilter filter;

    @BeforeEach
    void setUp() {
        JwtProperties props = new JwtProperties();
        props.setSecret("user-secret-at-least-32-bytes-long-123");
        props.setAdminSecret("admin-secret-at-least-32-bytes-4567");
        props.setAccessTokenTtl(java.time.Duration.ofMinutes(15));
        props.setRefreshTokenTtl(java.time.Duration.ofDays(1));
        tokens = new JwtTokenProvider(props);
        tokens.init();
        revocations = mock(TokenRevocationService.class);
        filter = new JwtAuthenticationFilter(tokens, revocations, mapper);
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void revokedJtiTriggers401WithTokenRevokedCode() throws Exception {
        String jti = UUID.randomUUID().toString();
        JwtTokenProvider.IssuedToken issued = tokens.issueAccessToken("user-1", Role.CUSTOMER);
        // issueAccessToken 会生成自己的 jti,我们要 mock Claims.getId() 返回上面的 jti
        // 但 IssuedToken 是真实的;直接用 issued.token() 即可,filter 解析时会拿到真实 jti。
        // 这里直接 mock 撤销服务:真实 jti 命中
        when(revocations.isRevoked(issued.jti(), "user-1")).thenReturn(true);

        MockHttpServletRequest req = new MockHttpServletRequest("GET", "/api/users/me");
        req.addHeader("Authorization", "Bearer " + issued.token());
        MockHttpServletResponse res = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        filter.doFilter(req, res, chain);

        assertThat(res.getStatus()).isEqualTo(401);
        assertThat(res.getContentAsString()).contains("\"code\":\"TOKEN_REVOKED\"");
        verify(chain, never()).doFilter(any(), any());
    }

    @Test
    void unRevokedJtiPassesThroughAndSetsSecurityContext() throws Exception {
        when(revocations.isRevoked(any(), any())).thenReturn(false);
        JwtTokenProvider.IssuedToken issued = tokens.issueAccessToken("user-1", Role.CUSTOMER);

        MockHttpServletRequest req = new MockHttpServletRequest("GET", "/api/users/me");
        req.addHeader("Authorization", "Bearer " + issued.token());
        MockHttpServletResponse res = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        filter.doFilter(req, res, chain);

        verify(chain).doFilter(any(), any());
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNotNull();
        UserPrincipal p = (UserPrincipal) SecurityContextHolder.getContext()
                .getAuthentication().getPrincipal();
        assertThat(p.getId()).isEqualTo("user-1");
        assertThat(p.getRole()).isEqualTo(Role.CUSTOMER);
    }

    @Test
    void invalidTokenClearsContextButPassesThrough() throws Exception {
        MockHttpServletRequest req = new MockHttpServletRequest("GET", "/api/users/me");
        req.addHeader("Authorization", "Bearer not.a.valid.token");
        MockHttpServletResponse res = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        filter.doFilter(req, res, chain);

        verify(chain).doFilter(any(), any());
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        // 撤销服务不应该被调用(parse 失败)
        verify(revocations, never()).isRevoked(any(), any());
    }

    @Test
    void missingAuthorizationHeaderPassesThrough() throws Exception {
        MockHttpServletRequest req = new MockHttpServletRequest("GET", "/api/users/me");
        MockHttpServletResponse res = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        filter.doFilter(req, res, chain);

        verify(chain).doFilter(any(), any());
        verify(revocations, never()).isRevoked(any(), any());
    }
}
