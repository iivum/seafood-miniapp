package com.seafood.shared.observability;

import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;

/**
 * 可观测性配置(OpenSpec setup-observability-stack PR #1,design §D3 / §D4)。
 *
 * <p>显式 {@link FilterRegistrationBean} 注册 {@link RequestIdFilter} 走 servlet
 * filter 链 — <em>早于</em> Spring Security 的 {@code FilterChainProxy},保证
 * JWT 鉴权失败 / 限流 429 / 全局异常 500 这三条路径的响应都带
 * {@code X-Request-Id} 头。设计意图见 design §D4 + spec §Filter ordering。
 *
 * <p>顺序契约(design §D4 + spec §Filter ordering):
 * <pre>
 *   RequestIdFilter.order = Ordered.HIGHEST_PRECEDENCE + 100
 *     ↓
 *   Spring Security FilterChainProxy(包含 SecurityHeadersFilter / JwtAuthenticationFilter / AdminRateLimitFilter)
 *     ↓
 *   DispatcherServlet → Controller
 * </pre>
 * 这样 401 / 429 / 500 响应在写回 client 之前,RequestIdFilter 早已把 X-Request-Id
 * 写到 response headers。
 */
@Configuration
public class ObservabilityConfig {

    /**
     * 单独注册 {@link RequestIdFilter} 为 Spring bean ——
     * {@link FilterRegistrationBean} 只是 servlet 注册 holder,不会把内嵌 filter
     * 暴露为 Spring bean。{@code RequestIdFilterOrderIT} 等测试需要 {@code @Autowired
     * RequestIdFilter},必须有这个独立 bean。
     */
    @Bean
    public RequestIdFilter requestIdFilterInstance() {
        return new RequestIdFilter();
    }

    /**
     * 把 {@link RequestIdFilter} 装到 servlet 链,URL pattern {@code /*},order 见类注释。
     */
    @Bean
    public FilterRegistrationBean<RequestIdFilter> requestIdFilter() {
        FilterRegistrationBean<RequestIdFilter> registration = new FilterRegistrationBean<>();
        registration.setFilter(requestIdFilterInstance());
        registration.setOrder(Ordered.HIGHEST_PRECEDENCE + 100);
        registration.addUrlPatterns("/*");
        registration.setName("requestIdFilter");
        return registration;
    }
}
