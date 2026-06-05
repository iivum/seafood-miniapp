package com.seafood.shared.error;

import com.seafood.user.application.AccountLockedException;
import org.junit.jupiter.api.BeforeEach;
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
 * Sprint 2 §3.10 — {@link AccountLockedException} 由 {@code GlobalExceptionHandler}
 * 翻译为 HTTP 423 + {@code Retry-After} 头 + {@code code=ACCOUNT_LOCKED} body。
 *
 * <p>与 {@code ErrorResponseRateLimitTest} 同样的"最小 {@code @SpringBootTest} +
 * 手工 {@code MockMvcBuilders}"模式。
 */
@SpringBootTest(
        classes = AccountLockedExceptionTest.TestApp.class,
        webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@Import(GlobalExceptionHandler.class)
class AccountLockedExceptionTest {

    @Autowired private WebApplicationContext ctx;
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.webAppContextSetup(ctx).build();
    }

    @Test
    void accountLockedExceptionTranslatesTo423WithRetryAfterAndCode() throws Exception {
        mvc.perform(MockMvcRequestBuilders.get("/test/locked"))
                .andExpect(status().isLocked()) // 423
                .andExpect(MockMvcResultMatchers.header().string(HttpHeaders.RETRY_AFTER, "120"))
                .andExpect(jsonPath("$.code").value("ACCOUNT_LOCKED"))
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("120")));
    }

    @SpringBootConfiguration
    @EnableAutoConfiguration
    @RestController
    static class TestApp {
        @GetMapping("/test/locked")
        public void bang() {
            throw new AccountLockedException(120);
        }
    }
}
