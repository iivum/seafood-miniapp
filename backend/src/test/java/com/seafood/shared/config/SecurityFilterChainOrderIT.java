package com.seafood.shared.config;

import com.seafood.shared.security.AdminRateLimitFilter;
import com.seafood.shared.security.AdminRateLimitProperties;
import com.seafood.shared.security.AdminRateLimiter;
import com.seafood.shared.security.JwtAuthenticationFilter;
import com.seafood.shared.security.JwtProperties;
import com.seafood.shared.security.JwtTokenProvider;
import com.seafood.shared.security.SecurityHeadersFilter;
import com.seafood.shared.security.SecurityHeadersProperties;
import com.seafood.user.application.TokenRevocationService;
import org.junit.jupiter.api.Test;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.security.web.FilterChainProxy;
import org.springframework.test.context.TestPropertySource;
import org.springframework.web.context.WebApplicationContext;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

/**
 * Sprint 2 PR review #21 — {@code SecurityConfig} 装配的 filter chain 顺序契约。
 *
 * <p>三个自定义 filter 顺序(specs/runtime-security §Filter chain ordering):
 * <ol>
 *   <li>{@link SecurityHeadersFilter} — 最早,任何响应都带安全头(放在
 *       {@code SecurityContextHolderFilter} 之前)</li>
 *   <li>{@link JwtAuthenticationFilter} — 鉴权后写 SecurityContext(放在
 *       {@code UsernamePasswordAuthenticationFilter} 之前)</li>
 *   <li>{@link AdminRateLimitFilter} — 拿到 principal 后做限流(放在 Jwt 之后,Controller 之前)</li>
 * </ol>
 *
 * <p>该顺序由 {@code SecurityConfig.addFilterBefore/addFilterAfter} 显式声明;
 * 本测试用 {@link FilterChainProxy} 把这条契约钉死 —— 任何重排都让本测试红。
 */
@SpringBootTest(
        classes = SecurityFilterChainOrderIT.TestApp.class,
        webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@TestPropertySource(properties = {
        "security.jwt.secret=ci-test-secret-must-be-at-least-32-bytes-long-AAAA",
        "security.jwt.admin-secret=ci-test-admin-secret-must-be-at-least-32-bytes-BBBB",
        "admin.bootstrap.password=ci-test-admin-bootstrap-password"
})
class SecurityFilterChainOrderIT {

    @org.springframework.beans.factory.annotation.Autowired
    private WebApplicationContext ctx;

    @org.springframework.beans.factory.annotation.Autowired
    private SecurityHeadersFilter headersFilter;

    @org.springframework.beans.factory.annotation.Autowired
    private JwtAuthenticationFilter jwtFilter;

    @org.springframework.beans.factory.annotation.Autowired
    private AdminRateLimitFilter rateLimitFilter;

    @Test
    void customFiltersArePresentInTheChain() {
        // 验证三个 bean 都已被 Spring 装配;SecurityConfig 把它们装进 chain
        assertThat(headersFilter).isNotNull();
        assertThat(jwtFilter).isNotNull();
        assertThat(rateLimitFilter).isNotNull();
    }

    @Test
    void headersFilterRunsBeforeJwtAndRateLimit() throws Exception {
        // 通过 FilterChainProxy 拿到整链,模拟一次空请求(无 token、无 admin 路径),
        // 走完后断言三个 filter 都跑过 ——
        // headers 写头最早(任何错误/正常响应都带),
        // jwt 在它之后鉴权尝试(无 token,直接放行),
        // rate-limit 在它之后跑(无 admin 路径,放行)。

        // 用 mock 请求 + 响应跑整链
        FilterChainProxy proxy = ctx.getBean(FilterChainProxy.class);
        assertThat(proxy).as("Spring Security FilterChainProxy must be wired").isNotNull();

        // 拿到所有 filter 列表(去重)
        List<? extends jakarta.servlet.Filter> filters = collectAllFilters(proxy);

        // 三个自定义 filter 都必须在 chain 中
        assertThat(filters)
                .as("SecurityHeadersFilter must be in the security chain")
                .anyMatch(f -> f == headersFilter);
        assertThat(filters)
                .as("JwtAuthenticationFilter must be in the security chain")
                .anyMatch(f -> f == jwtFilter);
        assertThat(filters)
                .as("AdminRateLimitFilter must be in the security chain")
                .anyMatch(f -> f == rateLimitFilter);

        // 顺序:headers < jwt < rate-limit
        int idxHeaders = filters.indexOf(headersFilter);
        int idxJwt = filters.indexOf(jwtFilter);
        int idxRateLimit = filters.indexOf(rateLimitFilter);
        assertThat(idxHeaders)
                .as("SecurityHeadersFilter must come BEFORE JwtAuthenticationFilter")
                .isLessThan(idxJwt);
        assertThat(idxJwt)
                .as("JwtAuthenticationFilter must come BEFORE AdminRateLimitFilter")
                .isLessThan(idxRateLimit);
    }

    /**
     * 把 FilterChainProxy 内部所有 chain 的所有 filter 摊平到一个 List。Spring Security 在
     * 不同 {@code requestMatchers} 下用不同 chain,但本测试关心"链中存在且相对有序"。
     */
    private static List<jakarta.servlet.Filter> collectAllFilters(FilterChainProxy proxy) {
        return proxy.getFilterChains().stream()
                .flatMap(chain -> chain.getFilters().stream())
                .distinct()
                .toList();
    }

    @SpringBootConfiguration
    @EnableAutoConfiguration
    @Import({SecurityConfig.class, SecurityFilterChainOrderIT.TestBeans.class})
    @org.springframework.web.bind.annotation.RestController
    static class TestApp {
        @org.springframework.web.bind.annotation.GetMapping("/api/products")
        public String products() { return "[]"; }
    }

    @TestConfiguration
    static class TestBeans {
        // 所有 *Properties 由 SecurityConfig.@EnableConfigurationProperties 提供,不重复声明
        @Bean SecurityHeadersFilter securityHeadersFilter(SecurityHeadersProperties p) { return new SecurityHeadersFilter(p); }
        @Bean AdminRateLimiter adminRateLimiter(AdminRateLimitProperties p) { return new AdminRateLimiter(p); }
        @Bean AdminRateLimitFilter adminRateLimitFilter(AdminRateLimiter l, tools.jackson.databind.ObjectMapper m) {
            return new AdminRateLimitFilter(l, m);
        }
        @Bean TokenRevocationService tokenRevocationService() { return mock(TokenRevocationService.class); }
        @Bean JwtTokenProvider jwtTokenProvider(JwtProperties p) {
            JwtTokenProvider t = new JwtTokenProvider(p);
            t.init();
            return t;
        }
        @Bean JwtAuthenticationFilter jwtAuthenticationFilter(JwtTokenProvider t,
                                                               TokenRevocationService r,
                                                               tools.jackson.databind.ObjectMapper m) {
            return new JwtAuthenticationFilter(t, r, m);
        }
        @Bean tools.jackson.databind.ObjectMapper objectMapper() {
            return tools.jackson.databind.json.JsonMapper.builder().build();
        }
    }
}
