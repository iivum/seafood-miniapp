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
 *   <li>XFF 头解析</li>
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

    @Test
    void usesXForwardedForWhenPresent() throws Exception {
        AdminRateLimiter limiter = mock(AdminRateLimiter.class);
        org.mockito.Mockito.when(limiter.tryAcquire(org.mockito.ArgumentMatchers.anyString()))
                .thenReturn(AdminRateLimiter.Decision.grant());
        AdminRateLimitFilter filter = new AdminRateLimitFilter(limiter, mapper);

        MockHttpServletRequest req = new MockHttpServletRequest("GET", "/api/admin/products");
        req.addHeader("X-Forwarded-For", "203.0.113.7, 10.0.0.1");
        filter.doFilter(req, new MockHttpServletResponse(), mock(FilterChain.class));

        org.mockito.ArgumentCaptor<String> captor = org.mockito.ArgumentCaptor.forClass(String.class);
        org.mockito.Mockito.verify(limiter).tryAcquire(captor.capture());
        String key = captor.getValue();
        assertThat(key).startsWith("203.0.113.7:");
    }
}
