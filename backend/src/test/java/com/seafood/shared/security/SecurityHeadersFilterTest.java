package com.seafood.shared.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

/**
 * Sprint 2 §2.2 — {@link SecurityHeadersFilter} 把 6 个头写到每个响应。
 *
 * <p>纯单元测试(不拉 Spring),用 Spring 自带的 {@code MockHttpServletResponse} 验
 * 证响应头真实落库,并断言下游 FilterChain 一定被调用。
 */
class SecurityHeadersFilterTest {

    private static final SecurityHeadersProperties DEFAULT_PROPS = new SecurityHeadersProperties();

    @Test
    void writesAllSixBaselineHeaders() throws Exception {
        SecurityHeadersFilter filter = new SecurityHeadersFilter(DEFAULT_PROPS);
        MockHttpServletResponse res = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        filter.doFilter(new MockHttpServletRequest("GET", "/api/products"), res, chain);

        assertThat(res.getHeader("Strict-Transport-Security")).isEqualTo("max-age=31536000; includeSubDomains");
        assertThat(res.getHeader("X-Content-Type-Options")).isEqualTo("nosniff");
        assertThat(res.getHeader("X-Frame-Options")).isEqualTo("DENY");
        assertThat(res.getHeader("Referrer-Policy")).isEqualTo("strict-origin-when-cross-origin");
        assertThat(res.getHeader("Permissions-Policy")).isEqualTo("geolocation=(), microphone=(), camera=()");
        assertThat(res.getHeader("Content-Security-Policy"))
                .isEqualTo("default-src 'self'; img-src 'self' data: https:; "
                        + "style-src 'self' 'unsafe-inline'; script-src 'self'");
        verify(chain).doFilter((HttpServletRequest) org.mockito.ArgumentMatchers.any(),
                (HttpServletResponse) org.mockito.ArgumentMatchers.any());
    }

    @Test
    void honorsOverriddenPropertyValues() throws Exception {
        SecurityHeadersProperties p = new SecurityHeadersProperties();
        p.setXFrameOptions("SAMEORIGIN");
        p.setContentSecurityPolicy("default-src 'none'");

        SecurityHeadersFilter filter = new SecurityHeadersFilter(p);
        MockHttpServletResponse res = new MockHttpServletResponse();

        filter.doFilter(new MockHttpServletRequest("GET", "/admin/index.html"), res, mock(FilterChain.class));

        assertThat(res.getHeader("X-Frame-Options")).isEqualTo("SAMEORIGIN");
        assertThat(res.getHeader("Content-Security-Policy")).isEqualTo("default-src 'none'");
        // 其它未改的头保持默认
        assertThat(res.getHeader("Strict-Transport-Security")).isEqualTo("max-age=31536000; includeSubDomains");
    }

    @Test
    void writesHeadersOnAdminStaticPathsToo() throws Exception {
        SecurityHeadersFilter filter = new SecurityHeadersFilter(DEFAULT_PROPS);
        MockHttpServletResponse res = new MockHttpServletResponse();

        filter.doFilter(new MockHttpServletRequest("GET", "/admin/index.html"), res, mock(FilterChain.class));

        assertThat(res.getHeaderNames()).containsAll(SecurityHeadersFilter.MANAGED_HEADERS);
    }
}
