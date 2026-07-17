package com.seafood.shared.error;

import com.seafood.shared.config.SecurityConfig;
import com.seafood.shared.security.AdminRateLimitFilter;
import com.seafood.shared.security.AdminRateLimitProperties;
import com.seafood.shared.security.AdminRateLimiter;
import com.seafood.shared.security.JwtAuthenticationFilter;
import com.seafood.shared.security.JwtProperties;
import com.seafood.shared.security.JwtTokenProvider;
import com.seafood.shared.security.Role;
import com.seafood.shared.security.SecurityHeadersFilter;
import com.seafood.shared.security.SecurityHeadersProperties;
import com.seafood.user.application.TokenRevocationService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Profile;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.context.WebApplicationContext;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * fix-error-contract-denyall task 1.1-1.3:未分类异常必须以
 * {@code 500 + {code:"INTERNAL", message}} 契约返回，不能沿 Spring Boot 默认
 * {@code /error} 重定向撞上 {@code SecurityConfig} 的 {@code anyRequest().denyAll()}
 * 兜底伪装成 403 空 body。
 *
 * <p>走真 filter chain（同 {@code SecurityFilterChainOrderIT} 模式，非
 * {@code @WebMvcTest} slice——后者绕过 filter chain，抓不到这类"漏配/denyAll 拦截"
 * 发生在 filter chain 层的 bug）。
 */
@SpringBootTest(
        classes = GlobalExceptionHandlerContractIT.TestApp.class,
        webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@TestPropertySource(properties = {
        "security.jwt.secret=ci-test-secret-must-be-at-least-32-bytes-long-AAAA",
        "security.jwt.admin-secret=ci-test-admin-secret-must-be-at-least-32-bytes-BBBB",
        "admin.bootstrap.password=ci-test-admin-bootstrap-password"
})
class GlobalExceptionHandlerContractIT {

    @Autowired
    private WebApplicationContext ctx;

    private MockMvc mvc() {
        return MockMvcBuilders.webAppContextSetup(ctx).apply(springSecurity()).build();
    }

    @Test
    void unclassifiedException_returns500WithInternalCode_notDenyAll403() throws Exception {
        mvc().perform(get("/api/products"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.code").value("INTERNAL"))
                .andExpect(jsonPath("$.message").exists());
    }

    @Test
    void knownException_notFound_behaviorUnchanged() throws Exception {
        mvc().perform(get("/api/banners"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"));
    }

    @Test
    void errorPath_isReachable_notBlockedByDenyAll() throws Exception {
        var result = mvc().perform(get("/error").accept(MediaType.APPLICATION_JSON)).andReturn();

        assertThat(result.getResponse().getStatus())
                .as("/error 不应被 anyRequest().denyAll() 拦成 403")
                .isNotEqualTo(403);
    }

    @Test
    void methodLevelPreAuthorizeDenial_returns403_notSwallowedByCatchAll() throws Exception {
        // 回归（本次实现过程中实测撞见）：加了 Exception.class 兜底后，
        // @PreAuthorize 拒绝抛出的 AccessDeniedException 一度被兜底吞成 500——
        // /api/orders/** 在 SecurityConfig 只要求 authenticated()，CUSTOMER token
        // 能过 URL 级过滤，真正命中的是方法级 @PreAuthorize 拒绝（异常在
        // DispatcherServlet 分发期间抛出，同 10 个既有 "*_asCustomer_returns403"
        // 切片测试撞见的机制）。
        JwtTokenProvider tokens = ctx.getBean(JwtTokenProvider.class);
        String token = tokens.issueAccessToken("u-1", Role.CUSTOMER).token();

        mvc().perform(get("/api/orders/admin-only-probe").header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
    }

    @Test
    void errorPath_rendersContractShape_notSpringBootDefaultShape() throws Exception {
        // task 2.4:直接命中 /error（无 ControllerAdvice 可覆盖的来源，如 filter 层异常/
        // 404 无 handler）时，body 也必须是 {code,message} 形状，不是 Spring Boot 默认的
        // {timestamp,status,error,path}。
        mvc().perform(get("/error").accept(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.code").exists())
                .andExpect(jsonPath("$.message").exists())
                .andExpect(jsonPath("$.timestamp").doesNotExist())
                .andExpect(jsonPath("$.path").doesNotExist());
    }

    @SpringBootConfiguration
    @EnableAutoConfiguration
    @Import({SecurityConfig.class, GlobalExceptionHandler.class, ContractErrorAttributes.class, TestBeans.class})
    @RestController
    static class TestApp {
        @GetMapping("/api/products")
        public String boom() {
            throw new IllegalArgumentException("simulated unclassified exception");
        }

        @GetMapping("/api/banners")
        public String notFound() {
            throw new NotFoundException("simulated not found");
        }

        @GetMapping("/api/orders/admin-only-probe")
        @PreAuthorize("hasRole('ADMIN')")
        public String adminOnlyProbe() {
            return "should never reach here as CUSTOMER";
        }
    }

    @TestConfiguration
    // 同 SecurityFilterChainOrderIT 的 native 规避理由：mock(TokenRevocationService.class)
    // 在 GraalVM nativeTest binary 下 static init 失败会 cascade 到 context 启动失败。
    @Profile("!native")
    static class TestBeans {
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
