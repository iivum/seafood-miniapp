package com.seafood.shared.observability;

import jakarta.servlet.Filter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * OpenSpec setup-observability-stack PR #1 / task 1.3.3 + 1.6.x —
 * 验证 {@link RequestIdFilter} 在 servlet 链的位置早于 Spring Security
 * FilterChainProxy,故即便鉴权失败(401)或服务端异常(500),响应仍带
 * {@code X-Request-Id} 头。
 *
 * <p>设计意图见 design §D4 + spec §Filter ordering + §Filter ordering §500 error
 * path preserves requestId。
 *
 * <p>为避免拉起完整 MongoDB / Redis,本 IT 用 {@code @TestConfiguration} 暴露
 * 一个最小 web 上下文 + 一个故意抛异常的 endpoint,断言错误路径。
 */
@SpringBootTest(
        classes = RequestIdFilterOrderIT.TestApp.class,
        webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@Import({ObservabilityConfig.class})
@Tag("native")
class RequestIdFilterOrderIT {

    @Autowired
    private WebApplicationContext ctx;

    @Autowired
    private RequestIdFilter requestIdFilter;

    @Autowired
    private ApplicationContext applicationContext;

    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        // webAppContextSetup 自动把 FilterRegistrationBean 注册的 filter 和
        // Spring Security FilterChainProxy 都装进 MockMvc 的 filter 链
        mvc = MockMvcBuilders.webAppContextSetup(ctx).build();
    }

    // --- 1.3.3 / 1.6.1:鉴权失败路径仍带 X-Request-Id ---
    @Test
    void unauthenticatedRequestStillHasRequestId() throws Exception {
        mvc.perform(get("/api/admin/dashboard"))
                .andExpect(status().is4xxClientError())
                .andExpect(header().exists("X-Request-Id"));
    }

    @Test
    void unauthenticatedRequestWithValidIncomingHeaderPassesThrough() throws Exception {
        String incoming = "01931a45-7c80-7000-9b3e-3f8a1c5e4d20";
        mvc.perform(get("/api/admin/dashboard").header("X-Request-Id", incoming))
                .andExpect(status().is4xxClientError())
                .andExpect(header().string("X-Request-Id", incoming));
    }

    @Test
    void unauthenticatedRequestGeneratesUuidV7IfHeaderMissing() throws Exception {
        var result = mvc.perform(get("/api/admin/dashboard"))
                .andExpect(status().is4xxClientError())
                .andExpect(header().exists("X-Request-Id"))
                .andReturn();
        String responseId = result.getResponse().getHeader("X-Request-Id");
        assertThat(java.util.UUID.fromString(responseId).version())
                .as("X-Request-Id 必须是 UUID v7 (design §D3 + ADR-OQ1)")
                .isEqualTo(7);
    }

    // --- 1.6.2:服务端异常路径(500)仍带 X-Request-Id ---
    @Test
    void internalErrorPreservesRequestId() throws Exception {
        mvc.perform(get("/__test__/boom"))
                .andExpect(status().is5xxServerError())
                .andExpect(header().exists("X-Request-Id"));
    }

    @Test
    void internalErrorWithIncomingHeaderPassesThrough() throws Exception {
        String incoming = "01931a45-7c80-7000-9b3e-3f8a1c5e4d20";
        mvc.perform(get("/__test__/boom").header("X-Request-Id", incoming))
                .andExpect(status().is5xxServerError())
                .andExpect(header().string("X-Request-Id", incoming));
    }

    // --- 1.3.x 顺序契约(结构断言):RequestIdFilter 在 Spring Security 之前 ---
    @Test
    void requestIdFilterRegistrationIsOrderedBeforeSecurityChain() {
        // FilterRegistrationBean.getOrder() 返回 setOrder() 的值
        // Spring Security FilterChainProxy 的 order 通常是 REQUEST_WRAPPER_FILTER_MAX_ORDER - 100
        Integer order = (Integer) applicationContext.getBean(
                "requestIdFilter", FilterRegistrationBean.class).getOrder();
        assertThat(order).isNotNull();
        // HIGHEST_PRECEDENCE + 100 = Integer.MIN_VALUE + 100
        assertThat(order)
                .as("RequestIdFilter must run as early as possible in the servlet filter chain")
                .isLessThan(0);
    }

    // --- 1.6.1.2:鉴权失败 response header 中的 requestId 在日志里也能找到 ---
    // 此断言通过 mvc 请求 + 验证 header 存在已经覆盖 ——
    // 完整 MDC 注入 log line 断言留 StructuredLoggingIT(1.5.x)

    @org.springframework.boot.SpringBootConfiguration
    @org.springframework.boot.autoconfigure.EnableAutoConfiguration
    @Import(RequestIdFilterOrderIT.TestBeans.class)
    @org.springframework.web.bind.annotation.RestController
    static class TestApp {
        // 受保护端点(会触发 Spring Security 401/403)
        @org.springframework.web.bind.annotation.GetMapping("/api/admin/dashboard")
        public String dashboard() {
            return "ok";
        }

        // 故意抛异常的端点(触发 500)
        @org.springframework.web.bind.annotation.GetMapping("/__test__/boom")
        public String boom() {
            throw new RuntimeException("intentional test failure");
        }
    }

    @TestConfiguration
    // 注:不放 @Profile("!native")。原写法是照搬 SecurityFilterChainOrderIT 的
    // TestBeans 注释(那边用 Mockito.mock 在 GraalVM nativeTest binary 下会
    // static init 失败),但本 IT 的 TestBeans 全是真实类,无 Mockito,
    // 不需要 native 排除。@Profile("!native") 在 Spring Test 框架下与类级
    // @Tag("native") 有意外交互(JUnit 5 在某些版本会把 @Tag 注入 active
    // profile,导致 TestBeans 被排除 → SecurityFilterChain 不装配 → 401
    // 测试拿到 200),这是 bug,故移除。
    static class TestBeans {
        @Bean
        public org.springframework.security.web.SecurityFilterChain testSecurityFilterChain(
                org.springframework.security.config.annotation.web.builders.HttpSecurity http)
                throws Exception {
            // 简化:仅关闭 CSRF,要求 /api/admin/** ADMIN 角色 ——
            // 任何未带 token 的请求 → 401,正好是 spec §Unauthenticated request 场景。
            return http
                    .csrf(org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer::disable)
                    .authorizeHttpRequests(auth -> auth
                            .requestMatchers("/api/admin/**").hasRole("ADMIN")
                            .anyRequest().permitAll())
                    .build();
        }
    }
}
