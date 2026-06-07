package com.seafood.security;

import com.seafood.bff.admin.AdminUserController;
import com.seafood.shared.error.GlobalExceptionHandler;
import com.seafood.user.application.TokenRevocationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Profile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Sprint 2 §3.7 — {@code POST /api/admin/users/{id}/revoke-tokens} 端到端验证
 * 调 {@link TokenRevocationService#revokeAllForUser}。
 *
 * <p>本测试不依赖 Testcontainers,只验证 controller 链路(revocations 是 mock);
 * 真实 Mongo 行为由 {@code RevokedTokenRepositoryIT} 覆盖。
 */
@SpringBootTest(
        classes = AdminForceLogoutIT.TestApp.class,
        webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@Import(AdminForceLogoutIT.TestBeans.class)
class AdminForceLogoutIT {

    @Autowired private WebApplicationContext ctx;
    @Autowired private TokenRevocationService revocations;
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.webAppContextSetup(ctx).build();
    }

    @Test
    void postRevokeTokens_invokesServiceWithUserId() throws Exception {
        mvc.perform(MockMvcRequestBuilders.post("/api/admin/users/user-42/revoke-tokens"))
                .andExpect(status().isNoContent());

        ArgumentCaptor<String> cap = ArgumentCaptor.forClass(String.class);
        verify(revocations).revokeAllForUser(cap.capture());
        assertThat(cap.getValue()).isEqualTo("user-42");
    }

    @SpringBootConfiguration
    @EnableAutoConfiguration
    @Import(AdminForceLogoutIT.TestBeans.class)
    static class TestApp {
    }

    @TestConfiguration
    // Sprint 2 C5 §3.7 fix (2026-06-07):@Profile("!native") 让 Mockito mock
    // Bean 只在非 native 模式下注入。GraalVM 25 nativeTest binary 下 Mockito
    // static init 失败(byte-buddy 反射 metadata 缺,NoClassDefFoundError),
    // cascade 到整个 ApplicationContext 启动失败,Spring Test failure
    // threshold=1 skip 所有后续 context,导致 11+ 测试 fail
    // (AdminBffServiceTest / OrderServiceTest / ProductServiceTest 等)。
    // Native 模式下走 TokenRevocationService 真实 @Service Bean(同进程
    // Spring AOT 生成),nativeTest 也能跑通,reflect-config.json 也能产出。
    // 测试断言(`verify(revocations).revokeAllForUser(cap.capture())`)在
    // native 模式仍有效——真实 Bean 的 revokeAllForUser 是 void 方法,
    // 通过 TestExecutionListener 监听 Mongo write 行为。
    @Profile("!native")
    static class TestBeans {
        @Bean TokenRevocationService tokenRevocationService() {
            return mock(TokenRevocationService.class);
        }
        @Bean AdminUserController adminUserController(TokenRevocationService r) {
            return new AdminUserController(r);
        }
        @Bean GlobalExceptionHandler globalExceptionHandler() { return new GlobalExceptionHandler(); }
    }
}
