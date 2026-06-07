package com.seafood.shared.security;

import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import tools.jackson.databind.json.JsonMapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 * Sprint 2 §2.6 — {@link AdminRateLimitFilter} 作用域与触发逻辑。
 *
 * <p>覆盖:
 * <ul>
 *   <li>非 /api/admin/ 路径短路({@code shouldNotFilter})</li>
 *   <li>配额内放行,FilterChain 被调用</li>
 *   <li>配额耗尽:filter 写 429 + Retry-After + RATE_LIMITED body,不再抛异常</li>
 *   <li>路径边界:精确匹配 {@code /api/admin}(无尾斜杠)</li>
 *   <li>路径边界:不应被近似前缀绕过(例如 {@code /api/adminalice})</li>
 *   <li>安全:不解析 {@code X-Forwarded-For} —— 否则攻击者可任意伪造 IP 绕过桶</li>
 * </ul>
 */
class AdminRateLimitFilterTest {

    private final tools.jackson.databind.ObjectMapper mapper = JsonMapper.builder().build();

    @Test
    void skipsNonAdminPaths() throws Exception {
        AdminRateLimiter limiter = mock(AdminRateLimiter.class);
        AdminRateLimitFilter filter = new AdminRateLimitFilter(limiter, mapper);
        FilterChain chain = mock(FilterChain.class);

        filter.doFilter(new MockHttpServletRequest("GET", "/api/products"), new MockHttpServletResponse(), chain);

        verify(limiter, never()).tryAcquire(org.mockito.ArgumentMatchers.anyString());
        verify(chain).doFilter(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
    }

    @Test
    void allowsWhenBucketHasCapacity() throws Exception {
        AdminRateLimiter limiter = mock(AdminRateLimiter.class);
        org.mockito.Mockito.when(limiter.tryAcquire(org.mockito.ArgumentMatchers.anyString()))
                .thenReturn(AdminRateLimiter.Decision.grant());
        AdminRateLimitFilter filter = new AdminRateLimitFilter(limiter, mapper);
        FilterChain chain = mock(FilterChain.class);

        MockHttpServletRequest req = new MockHttpServletRequest("GET", "/api/admin/dashboard");
        req.setRemoteAddr("10.0.0.1");
        MockHttpServletResponse res = new MockHttpServletResponse();

        filter.doFilter(req, res, chain);

        verify(chain).doFilter(req, res);
        assertThat(res.getStatus()).isEqualTo(200);
    }

    @Test
    void writesRateLimitedResponseWhenBucketExhausted() throws Exception {
        AdminRateLimiter limiter = mock(AdminRateLimiter.class);
        org.mockito.Mockito.when(limiter.tryAcquire(org.mockito.ArgumentMatchers.anyString()))
                .thenReturn(AdminRateLimiter.Decision.deny(37));
        AdminRateLimitFilter filter = new AdminRateLimitFilter(limiter, mapper);
        FilterChain chain = mock(FilterChain.class);

        MockHttpServletRequest req = new MockHttpServletRequest("GET", "/api/admin/dashboard");
        req.setRemoteAddr("10.0.0.1");
        MockHttpServletResponse res = new MockHttpServletResponse();

        filter.doFilter(req, res, chain);

        assertThat(res.getStatus()).isEqualTo(429);
        assertThat(res.getHeader(HttpHeaders.RETRY_AFTER)).isEqualTo("37");
        assertThat(res.getContentAsString()).contains("\"code\":\"RATE_LIMITED\"");
        verify(chain, never()).doFilter(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
    }

    /**
     * 路径边界:精确匹配 {@code /api/admin}(无尾斜杠)也走限流(PR review #4 回归保护)。
     * 之前实现用 {@code startsWith("/api/admin/")} 会放过这种请求,虽然业务上
     * {@code /api/admin} 单独访问会被 controller 路由 404,但仍然以"防御性 filter"
     * 的角色对它走限流 —— 与 {@link JwtAuthenticationFilter#isAdminPath(String)} 一致。
     */
    @Test
    void filtersExactAdminPathWithoutTrailingSlash() throws Exception {
        AdminRateLimiter limiter = mock(AdminRateLimiter.class);
        org.mockito.Mockito.when(limiter.tryAcquire(org.mockito.ArgumentMatchers.anyString()))
                .thenReturn(AdminRateLimiter.Decision.grant());
        AdminRateLimitFilter filter = new AdminRateLimitFilter(limiter, mapper);
        FilterChain chain = mock(FilterChain.class);

        MockHttpServletRequest req = new MockHttpServletRequest("GET", "/api/admin");
        req.setRemoteAddr("10.0.0.1");
        filter.doFilter(req, new MockHttpServletResponse(), chain);

        // 关键:limiter 必须被调,FilterChain 也必须被调
        org.mockito.Mockito.verify(limiter).tryAcquire(org.mockito.ArgumentMatchers.anyString());
        verify(chain).doFilter(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
    }

    /**
     * 路径边界:近似前缀不应被误判为 admin。
     */
    @Test
    void skipsSimilarPrefixesThatAreNotAdmin() throws Exception {
        AdminRateLimiter limiter = mock(AdminRateLimiter.class);
        AdminRateLimitFilter filter = new AdminRateLimitFilter(limiter, mapper);
        FilterChain chain = mock(FilterChain.class);

        filter.doFilter(new MockHttpServletRequest("GET", "/api/adminalice"), new MockHttpServletResponse(), chain);
        filter.doFilter(new MockHttpServletRequest("GET", "/api/adminfoo"), new MockHttpServletResponse(), chain);

        verify(limiter, never()).tryAcquire(org.mockito.ArgumentMatchers.anyString());
    }

    /**
     * 安全回归:filter 不可信任 {@code X-Forwarded-For} —— 否则攻击者每次请求带
     * {@code XFF: 1.2.3.4} 就能绕开按 IP 的桶,无限打 admin 登录。
     * 真实客户端 IP 由前置 nginx/ALB 在 TCP 层(spring {@code forward-headers-strategy: framework}
     * 不改 getRemoteAddr)负责,这里只用 {@link HttpServletRequest#getRemoteAddr()}。
     */
    @Test
    void ignoresSpoofedXForwardedFor() throws Exception {
        AdminRateLimiter limiter = mock(AdminRateLimiter.class);
        org.mockito.Mockito.when(limiter.tryAcquire(org.mockito.ArgumentMatchers.anyString()))
                .thenReturn(AdminRateLimiter.Decision.grant());
        AdminRateLimitFilter filter = new AdminRateLimitFilter(limiter, mapper);

        MockHttpServletRequest req = new MockHttpServletRequest("GET", "/api/admin/products");
        req.setRemoteAddr("10.0.0.1");                       // 真实 TCP 端
        req.addHeader("X-Forwarded-For", "203.0.113.7");     // 攻击者伪造
        filter.doFilter(req, new MockHttpServletResponse(), mock(FilterChain.class));

        org.mockito.ArgumentCaptor<String> captor = org.mockito.ArgumentCaptor.forClass(String.class);
        org.mockito.Mockito.verify(limiter).tryAcquire(captor.capture());
        String key = captor.getValue();
        assertThat(key)
                .as("桶 key 必须用 TCP 端 IP(10.0.0.1),不能用伪造的 XFF(203.0.113.7)")
                .startsWith("10.0.0.1:")
                .doesNotContain("203.0.113.7");
    }
}
