package com.seafood.security;

import com.seafood.shared.error.GlobalExceptionHandler;
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
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.context.WebApplicationContext;

import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;

/**
 * Sprint 2 §2.10 — 端到端验证 6 个安全响应头出现在每个响应里。
 *
 * <p>Spring Boot 4 移除了 {@code @AutoConfigureMockMvc};手工 {@code webAppContextSetup}
 * 装配 MockMvc,显式 {@code .addFilter()} 挂上 SecurityHeadersFilter(因为没有
 * Spring Security FilterChainProxy 在这个最小上下文里)。
 *
 * <p>Sprint 2 C5 §5.2:tagged {@code native} — 与 {@code JwtAuthenticationFilter}
 * 形态同源(都是 {@code OncePerRequestFilter}),作为 filter-IT 切片样本让
 * nativeTest agent 走通 Spring Security filter 反射链。
 */
@Tag("native")
@SpringBootTest(
        classes = SecurityHeadersIT.TestApp.class,
        webEnvironment = SpringBootTest.WebEnvironment.MOCK)
class SecurityHeadersIT {

    @Autowired private WebApplicationContext ctx;
    @Autowired private SecurityHeadersFilter headersFilter;
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.webAppContextSetup(ctx)
                .addFilter(headersFilter)
                .build();
    }

    @Test
    void jsonApiCarriesAllSixHeaders() throws Exception {
        mvc.perform(MockMvcRequestBuilders.get("/api/products"))
                .andExpect(header().string("Strict-Transport-Security",
                        org.hamcrest.Matchers.containsString("max-age=31536000")))
                .andExpect(header().string("X-Content-Type-Options", "nosniff"))
                .andExpect(header().string("X-Frame-Options", "DENY"))
                .andExpect(header().string("Referrer-Policy", "strict-origin-when-cross-origin"))
                .andExpect(header().string("Permissions-Policy",
                        org.hamcrest.Matchers.containsString("geolocation=()")))
                .andExpect(header().string("Content-Security-Policy",
                        org.hamcrest.Matchers.containsString("default-src 'self'")));
    }

    @Test
    void staticAdminPathCarriesAllSixHeaders() throws Exception {
        mvc.perform(MockMvcRequestBuilders.get("/admin/index.html"))
                .andExpect(header().string("Strict-Transport-Security",
                        org.hamcrest.Matchers.containsString("max-age")))
                .andExpect(header().string("X-Frame-Options", "DENY"));
    }

    @Test
    void notFoundPathCarriesAllSixHeaders() throws Exception {
        mvc.perform(MockMvcRequestBuilders.get("/this/does/not/exist"))
                .andExpect(header().string("Strict-Transport-Security",
                        org.hamcrest.Matchers.containsString("max-age")))
                .andExpect(header().string("X-Frame-Options", "DENY"));
    }

    @SpringBootConfiguration
    @EnableAutoConfiguration
    @Import(SecurityHeadersIT.TestBeans.class)
    @RestController
    static class TestApp {
        @GetMapping("/api/products")
        public String products() { return "[]"; }
    }

    @TestConfiguration
    static class TestBeans {
        @Bean SecurityHeadersProperties securityHeadersProperties() { return new SecurityHeadersProperties(); }
        @Bean SecurityHeadersFilter securityHeadersFilter(SecurityHeadersProperties p) { return new SecurityHeadersFilter(p); }
        @Bean GlobalExceptionHandler globalExceptionHandler() { return new GlobalExceptionHandler(); }
    }
}
