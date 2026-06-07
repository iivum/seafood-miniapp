package com.seafood.shared.error;

import com.seafood.shared.security.RateLimitedException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.springframework.test.web.servlet.result.MockMvcResultMatchers;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.context.WebApplicationContext;

import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Sprint 2 §2.7 — {@link RateLimitedException} 在 GlobalExceptionHandler 中翻译为
 * HTTP 429 + {@code Retry-After} 头 + {@code code=RATE_LIMITED} body。
 *
 * <p>Spring Boot 4 移除了 {@code @AutoConfigureMockMvc};改用手工 {@code webAppContextSetup}
 * 拿 MockMvc,这样起一个最小 {@code @SpringBootTest} 即可。
 */
@SpringBootTest(
        classes = ErrorResponseRateLimitTest.TestApp.class,
        webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@Import(GlobalExceptionHandler.class)
class ErrorResponseRateLimitTest {

    @Autowired private WebApplicationContext ctx;
    private MockMvc mvc;

    @org.junit.jupiter.api.BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.webAppContextSetup(ctx).build();
    }

    @Test
    void rateLimitedExceptionTranslatesTo429WithRetryAfterAndCode() throws Exception {
        mvc.perform(MockMvcRequestBuilders.get("/test/rate-limited"))
                .andExpect(status().isTooManyRequests())
                .andExpect(MockMvcResultMatchers.header().string(HttpHeaders.RETRY_AFTER, "42"))
                .andExpect(jsonPath("$.code").value("RATE_LIMITED"))
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("42")));
    }

    @SpringBootConfiguration
    @EnableAutoConfiguration
    @RestController
    static class TestApp {
        @GetMapping("/test/rate-limited")
        public void bang() {
            throw new RateLimitedException(42);
        }
    }
}
