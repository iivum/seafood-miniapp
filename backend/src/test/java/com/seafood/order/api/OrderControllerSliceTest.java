package com.seafood.order.api;

import com.seafood.order.api.dto.OrderResponse;
import com.seafood.order.application.OrderService;
import com.seafood.shared.security.AdminRateLimiter;
import com.seafood.shared.security.JwtTokenProvider;
import com.seafood.shared.security.Role;
import com.seafood.shared.security.SecurityHeadersProperties;
import com.seafood.shared.security.UserPrincipal;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

/**
 * OrderController @WebMvcTest slice — 4 验证用例,覆盖
 * <ul>
 *   <li>权限矩阵(CUSTOMER list/get / CUSTOMER ship 拒 / ADMIN ship 过)</li>
 *   <li>404 异常经 {@code GlobalExceptionHandler} 翻译成 { code, message } 响应</li>
 *   <li>分页响应 JSON 结构(content[].id)</li>
 * </ul>
 */
@WebMvcTest(OrderController.class)
@EnableConfigurationProperties(SecurityHeadersProperties.class)
// @WebMvcTest 默认不引入 SecurityConfig 的 @EnableMethodSecurity;显式启
// 用,确保 @PreAuthorize 在 MockMvc 链路里被拦截,而不是悄悄走到 controller
// body。Spring Security 7 的 AccessDeniedExceptionResolver 在 @WebMvcTest 切片
// extendHandlerExceptionResolvers 时机不对 — 用 {@link AccessDeniedTestAdvice}
// 兜底翻译成 403。
@Import(OrderControllerSliceTest.MethodSecurityConfig.class)
class OrderControllerSliceTest {

    @TestConfiguration
    @EnableMethodSecurity
    @Import(AccessDeniedTestAdvice.class)
    static class MethodSecurityConfig {}

    /**
     * 测试用 AccessDeniedException → 403 翻译 — Spring Security 7 的
     * {@code AccessDeniedExceptionResolver} 在 @WebMvcTest 切片里 extend
     * handler exception resolvers 时机不对,补一个 {@code @ControllerAdvice}
     * 兜底,确保 {@code @PreAuthorize} 拒绝时 MockMvc 收到 403。
     */
    @RestControllerAdvice
    static class AccessDeniedTestAdvice {
        @ExceptionHandler(AccessDeniedException.class)
        public ResponseEntity<Void> handleAccessDenied(AccessDeniedException ex) {
            return ResponseEntity.status(403).build();
        }
    }

    @Autowired MockMvcTester mvc;
    @MockitoBean OrderService orderService;
    // @WebMvcTest auto-loads SecurityConfig; mock its filter collaborators.
    // AdminRateLimiter feeds AdminRateLimitFilter, JwtTokenProvider +
    // TokenRevocationService feed JwtAuthenticationFilter.
    @MockitoBean AdminRateLimiter adminRateLimiter;
    @MockitoBean JwtTokenProvider jwtTokenProvider;
    @MockitoBean com.seafood.user.application.TokenRevocationService tokenRevocationService;

    /**
     * 直接写 SecurityContextHolder — Spring Security 7 在 @WebMvcTest 切片下
     * {@code SecurityContextHolderFilter} 不一定从 {@code SecurityMockMvcRequestPostProcessors}
     * 保存的请求属性读回 SecurityContext,导致 @PreAuthorize 切面读不到 Authentication
     * → {@code AuthenticationCredentialsNotFoundException}。
     * 这里手工把 SecurityContext 写到 thread-local,@PreAuthorize 直接读这个,绕过
     * filter chain 顺序坑。
     */
    @BeforeEach
    void setUpAuth() {
        SecurityContext ctx = SecurityContextHolder.createEmptyContext();
        ctx.setAuthentication(authOf("u-1", Role.CUSTOMER));
        SecurityContextHolder.setContext(ctx);
    }

    @AfterEach
    void clearAuth() {
        SecurityContextHolder.clearContext();
    }

    private static Authentication authOf(String id, Role role) {
        UserPrincipal principal = new UserPrincipal(id, role);
        return new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities());
    }

    @Test
    void list_asCustomer_returnsPagedOrders() {
        Instant now = Instant.parse("2026-06-19T00:00:00Z");
        OrderResponse stub = new OrderResponse(
                "o-1", "u-1", List.of(), BigDecimal.ZERO,
                "PENDING", null, null, null, null, now, now);
        Page<OrderResponse> page = new PageImpl<>(List.of(stub), PageRequest.of(0, 20), 1);
        when(orderService.list(any(), any())).thenReturn(page);

        mvc.get().uri("/api/orders")
            .exchange()
            .assertThat()
            .hasStatusOk()
            .bodyJson()
            .hasPathSatisfying("$.content[0].id", v -> v.assertThat().isEqualTo("o-1"));
    }

    /**
     * OrderController.get 实现是 list(0,1) + filter by id — 命中空 → 抛 NotFoundException。
     * 测试空页 stub 来触发 404 + { code, message } body。
     */
    @Test
    void get_notFound_returns404() {
        Page<OrderResponse> empty = new PageImpl<>(List.of(), PageRequest.of(0, 1), 0);
        when(orderService.list(eq(null), any(Pageable.class))).thenReturn(empty);

        mvc.get().uri("/api/orders/missing")
            .exchange()
            .assertThat()
            .hasStatus(404)
            .bodyJson()
            .hasPath("$.code");
    }

    /**
     * ship 方法 @PreAuthorize("hasRole('ADMIN')") — CUSTOMER 调用必须 403。
     * @BeforeEach 已写 CUSTOMER 进 SecurityContextHolder;切面走到 @PreAuthorize
     * 直接判定 ROLE_CUSTOMER ≠ ROLE_ADMIN → AuthorizationDeniedException。
     *
     * <p>Spring Security 7 默认由 {@code AccessDeniedExceptionResolver} 翻译成 403,
     * 但 @WebMvcTest 切片里该 resolver 注册不生效。本测试在 {@link AccessDeniedTestAdvice}
     * 内提供 {@code @ExceptionHandler(AccessDeniedException.class)} 兜底翻译为 403,
     * 保证断言通过。
     */
    @Test
    void ship_asCustomer_returns403() {
        mvc.post().uri("/api/orders/o-1/ship")
            .exchange()
            .assertThat()
            .hasStatus(403);
    }

    /**
     * ship ADMIN 路径 — 用 SecurityContextHolder 重写为本请求的 ADMIN,
     * 再调 ship,@PreAuthorize 通过,service 被 stub 返 SHIPPED 状态。
     */
    @Test
    void ship_asAdmin_returnsShippedOrder() {
        Instant now = Instant.parse("2026-06-19T00:00:00Z");
        OrderResponse stub = new OrderResponse(
                "o-1", "u-1", List.of(), BigDecimal.ZERO,
                "SHIPPED", null, null, null, null, now, now);
        when(orderService.ship("o-1")).thenReturn(stub);

        // 覆盖 @BeforeEach 的 CUSTOMER context → ADMIN
        SecurityContext ctx = SecurityContextHolder.createEmptyContext();
        ctx.setAuthentication(authOf("admin-1", Role.ADMIN));
        SecurityContextHolder.setContext(ctx);

        mvc.post().uri("/api/orders/o-1/ship")
            .exchange()
            .assertThat()
            .hasStatusOk()
            .bodyJson()
            .hasPathSatisfying("$.id", v -> v.assertThat().isEqualTo("o-1"));
    }
}
