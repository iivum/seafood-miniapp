package com.seafood.bff.admin;

import com.seafood.bff.admin.dto.DashboardResponse;
import com.seafood.bff.admin.dto.OrderStatsResponse;
import com.seafood.bff.admin.dto.TopProductResponse;
import com.seafood.bff.admin.dto.TrendPointResponse;
import com.seafood.order.api.dto.OrderResponse;
import com.seafood.order.application.OrderService;
import com.seafood.product.api.dto.ProductResponse;
import com.seafood.product.api.dto.ProductStatsResponse;
import com.seafood.product.application.ProductService;
import com.seafood.product.domain.ProductStatus;
import com.seafood.shared.security.AdminRateLimiter;
import com.seafood.shared.security.JwtTokenProvider;
import com.seafood.shared.security.Role;
import com.seafood.shared.security.SecurityHeadersProperties;
import com.seafood.shared.security.UserPrincipal;
import com.seafood.testsupport.builders.OrderBuilder;
import com.seafood.testsupport.builders.ProductBuilder;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
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
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@WebMvcTest(AdminBffController.class)
@EnableConfigurationProperties(SecurityHeadersProperties.class)
@Import(AdminBffControllerSliceTest.MethodSecurityConfig.class)
class AdminBffControllerSliceTest {

    @TestConfiguration
    @EnableMethodSecurity
    @Import(AccessDeniedTestAdvice.class)
    static class MethodSecurityConfig {}

    @RestControllerAdvice
    static class AccessDeniedTestAdvice {
        @ExceptionHandler(AccessDeniedException.class)
        public ResponseEntity<Void> handleAccessDenied(AccessDeniedException ex) {
            return ResponseEntity.status(403).build();
        }
    }

    @Autowired MockMvcTester mvc;
    @MockitoBean AdminBffService adminBffService;
    @MockitoBean com.seafood.user.application.UserService userService;
    @MockitoBean com.seafood.product.application.ProductQueryService productQueryService;
    @MockitoBean OrderService orderService;
    @MockitoBean ProductService productService;
    @MockitoBean AdminRateLimiter adminRateLimiter;
    @MockitoBean JwtTokenProvider jwtTokenProvider;
    @MockitoBean com.seafood.user.application.TokenRevocationService tokenRevocationService;

    @BeforeEach
    void setUpAdmin() {
        when(adminRateLimiter.tryAcquire(any())).thenReturn(AdminRateLimiter.Decision.grant());
        SecurityContext ctx = SecurityContextHolder.createEmptyContext();
        ctx.setAuthentication(authOf("admin-1", Role.ADMIN));
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

    private static ProductResponse sampleProduct(String id) {
        return new ProductResponse(id, "测试商品", "desc", new BigDecimal("99.00"),
            100, "鱼类", "https://img/" + id + ".jpg", ProductStatus.ACTIVE,
            Instant.parse("2026-06-19T00:00:00Z"), Instant.parse("2026-06-19T00:00:00Z"));
    }

    private static OrderResponse sampleOrder(String id) {
        var order = OrderBuilder.anOrder().withId(id).build();
        return OrderResponse.from(order);
    }

    @Test
    void dashboard_asAdmin_returnsAggregatedMetrics() {
        var productStats = new ProductStatsResponse(10L, 7L, 3L, Map.of("鱼类", 5L));
        var orderStats = new OrderStatsResponse(100L, 50L, 30L, BigDecimal.ZERO, BigDecimal.ZERO);
        var top = new TopProductResponse(sampleProduct("p-1"), 50L);
        var trend = new TrendPointResponse(java.time.LocalDate.parse("2026-06-19"), 5L);
        var dashboard = new DashboardResponse(orderStats, productStats, List.of(top), List.of(trend),
            List.of(sampleProduct("p-low")), List.of(sampleOrder("o-recent")));
        when(adminBffService.dashboard()).thenReturn(dashboard);

        mvc.get().uri("/api/admin/dashboard")
            .exchange()
            .assertThat()
            .hasStatusOk()
            .bodyJson()
            .hasPathSatisfying("$.recentOrders[0].id", v -> v.assertThat().isEqualTo("o-recent"));
    }

    @Test
    void productStats_asAdmin_returnsStats() {
        var stats = new ProductStatsResponse(10L, 7L, 3L, Map.of("鱼类", 5L, "虾蟹", 2L));
        when(adminBffService.productStats()).thenReturn(stats);

        mvc.get().uri("/api/admin/products/stats")
            .exchange()
            .assertThat()
            .hasStatusOk()
            .bodyJson()
            .hasPathSatisfying("$.total", v -> v.assertThat().isEqualTo(10));
    }

    @Test
    void dashboard_asCustomer_returns403() {
        SecurityContextHolder.clearContext();
        SecurityContext ctx = SecurityContextHolder.createEmptyContext();
        ctx.setAuthentication(authOf("u-1", Role.CUSTOMER));
        SecurityContextHolder.setContext(ctx);

        mvc.get().uri("/api/admin/dashboard")
            .exchange()
            .assertThat()
            .hasStatus(403);
    }
}
