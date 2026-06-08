package com.seafood.shared.observability;

import org.junit.jupiter.api.Test;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.core.Ordered;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * OpenSpec setup-observability-stack PR #1 / task 1.3.1-1.3.2 —
 * 验证 {@link ObservabilityConfig#requestIdFilter()} 暴露的
 * {@link FilterRegistrationBean} 配置正确:
 * <ul>
 *   <li>order = {@code HIGHEST_PRECEDENCE + 100} (design §D4,先于 Security FilterChainProxy)</li>
 *   <li>URL pattern = "/*"</li>
 *   <li>注册的 filter 是 {@link RequestIdFilter} 实例</li>
 * </ul>
 *
 * <p>plain JUnit — 不需要 Spring 容器。结构契约不会因运行时 bean
 * 装配问题失败,鲁棒性优于 {@code RequestIdFilterOrderIT}。
 */
class ObservabilityConfigTest {

    @Test
    void requestIdFilterRegistrationOrderIsHighPrecedence() {
        FilterRegistrationBean<RequestIdFilter> reg =
                new ObservabilityConfig().requestIdFilter();
        assertThat(reg.getOrder())
                .as("RequestIdFilter 必须早于 Spring Security FilterChainProxy "
                        + "(默认 order ≈ -100);HIGHEST_PRECEDENCE + 100 = MIN_VALUE + 100")
                .isEqualTo(Ordered.HIGHEST_PRECEDENCE + 100);
    }

    @Test
    void requestIdFilterRegistrationUrlPatternCoversAll() {
        FilterRegistrationBean<RequestIdFilter> reg =
                new ObservabilityConfig().requestIdFilter();
        assertThat(reg.getUrlPatterns())
                .as("RequestIdFilter 应作用于所有 URL,包括 actuator")
                .containsExactly("/*");
    }

    @Test
    void requestIdFilterRegistrationWiresActualFilterInstance() {
        FilterRegistrationBean<RequestIdFilter> reg =
                new ObservabilityConfig().requestIdFilter();
        assertThat(reg.getFilter())
                .as("FilterRegistrationBean 应持有 RequestIdFilter 实例")
                .isInstanceOf(RequestIdFilter.class);
    }
}
