package com.seafood.bff.admin;

import com.seafood.product.api.dto.ProductStatsResponse;
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

import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * AdminBffController productStats endpoint — asserts the
 * {@code byCategory} map is forwarded in the JSON response body
 * (路线图 2.18 库存预警的前置:ad-04 库存页消费此数据)。
 */
@WebMvcTest(AdminBffController.class)
@EnableConfigurationProperties(SecurityHeadersProperties.class)
@Import(AdminBffProductStatsDetailSliceTest.MethodSecurityConfig.class)
class AdminBffProductStatsDetailSliceTest {

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
    @MockitoBean com.seafood.order.application.OrderService orderService;
    @MockitoBean com.seafood.product.application.ProductService productService;
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

    @Test
    void productStats_byCategoryMap_isForwardedInJson() {
        var stats = new ProductStatsResponse(10L, 7L, 3L,
            Map.of("鱼类", 5L, "虾蟹", 2L, "贝类", 1L));
        when(adminBffService.productStats()).thenReturn(stats);

        mvc.get().uri("/api/admin/products/stats")
            .exchange()
            .assertThat()
            .hasStatusOk()
            .bodyJson()
            .hasPathSatisfying("$.byCategory.鱼类", v -> v.assertThat().isEqualTo(5))
            .hasPathSatisfying("$.onSale", v -> v.assertThat().isEqualTo(7));
    }
}
