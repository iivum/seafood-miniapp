package com.seafood.security;

import com.seafood.shared.error.GlobalExceptionHandler;
import com.seafood.shared.security.AdminRateLimitFilter;
import com.seafood.shared.security.AdminRateLimitProperties;
import com.seafood.shared.security.AdminRateLimiter;
import com.seafood.shared.security.SecurityHeadersFilter;
import com.seafood.shared.security.SecurityHeadersProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.context.WebApplicationContext;

import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Sprint 2 §2.9 — Admin 路径限流的端到端 IT。
 *
 * <p>最小装配 + 显式 {@code addFilter} 挂限流/头 filter(Spring Boot 4 无
 * {@code @AutoConfigureMockMvc})。限流阈值压到 3。
 *
 * <p>Sprint 2 C5 §5.2:tagged {@code native} — 作为 nativeTest 阶段 agent 收集
 * 反射/资源/代理 metadata 的 controller-IT 切片样本(覆盖 @Controller 路径 +
 * Servlet filter 链)。
 */
@Tag("native")
@SpringBootTest(
        classes = AdminRateLimitIT.TestApp.class,
        webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@TestPropertySource(properties = {
        "security.rate-limit.requests-per-minute=3",
        "security.rate-limit.bucket-ttl-seconds=60"
})
class AdminRateLimitIT {

    @Autowired private WebApplicationContext ctx;
    @Autowired private AdminRateLimitFilter rateLimitFilter;
    @Autowired private SecurityHeadersFilter headersFilter;
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.webAppContextSetup(ctx)
                .addFilter(headersFilter)
                .addFilter(rateLimitFilter)
                .build();
    }

    @Test
    void firstNRequestsPass_thenN_plus1Returns429() throws Exception {
        for (int i = 0; i < 3; i++) {
            mvc.perform(MockMvcRequestBuilders.get("/api/admin/dashboard"))
                    .andExpect(status().is(200));
        }
        mvc.perform(MockMvcRequestBuilders.get("/api/admin/dashboard"))
                .andExpect(status().isTooManyRequests())
                .andExpect(header().exists("Retry-After"))
                .andExpect(jsonPath("$.code").value("RATE_LIMITED"));
    }

    @Test
    void nonAdminPathIsNotRateLimited() throws Exception {
        for (int i = 0; i < 10; i++) {
            mvc.perform(MockMvcRequestBuilders.get("/api/products"))
                    .andExpect(status().is(org.hamcrest.Matchers.not(429)));
        }
    }

    @SpringBootConfiguration
    @EnableAutoConfiguration
    @Import(AdminRateLimitIT.TestBeans.class)
    @RestController
    static class TestApp {
        @GetMapping("/api/admin/dashboard")
        public String dashboard() { return "ok"; }

        @GetMapping("/api/products")
        public String products() { return "[]"; }
    }

    @TestConfiguration
    static class TestBeans {
        @Bean SecurityHeadersProperties securityHeadersProperties() { return new SecurityHeadersProperties(); }
        @Bean AdminRateLimitProperties adminRateLimitProperties() { return new AdminRateLimitProperties(); }
        @Bean SecurityHeadersFilter securityHeadersFilter(SecurityHeadersProperties p) { return new SecurityHeadersFilter(p); }
        @Bean AdminRateLimiter adminRateLimiter(AdminRateLimitProperties p) { return new AdminRateLimiter(p); }
        @Bean AdminRateLimitFilter adminRateLimitFilter(AdminRateLimiter l, tools.jackson.databind.ObjectMapper m) {
            return new AdminRateLimitFilter(l, m);
        }
        @Bean GlobalExceptionHandler globalExceptionHandler() { return new GlobalExceptionHandler(); }
        @Bean tools.jackson.databind.ObjectMapper objectMapper() {
            return tools.jackson.databind.json.JsonMapper.builder().build();
        }
    }
}
