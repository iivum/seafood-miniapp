package com.seafood.bff.admin;

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

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * AdminBffController ADMIN boundary defense — verifies the class-level
 * {@code @PreAuthorize("hasRole('ADMIN')")} blocks CUSTOMER calls across
 * all 3 endpoints (dashboard, productStats, orderDetail).
 */
@WebMvcTest(AdminBffController.class)
@EnableConfigurationProperties(SecurityHeadersProperties.class)
@Import(AdminBffAuthBoundarySliceTest.MethodSecurityConfig.class)
class AdminBffAuthBoundarySliceTest {

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
    void setUpCustomer() {
        when(adminRateLimiter.tryAcquire(any())).thenReturn(AdminRateLimiter.Decision.grant());
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
    void productStats_asCustomer_returns403() {
        mvc.get().uri("/api/admin/products/stats")
            .exchange()
            .assertThat()
            .hasStatus(403);
    }

    @Test
    void orderDetail_asCustomer_returns403() {
        mvc.get().uri("/api/admin/orders/o-1/detail")
            .exchange()
            .assertThat()
            .hasStatus(403);
    }
}
