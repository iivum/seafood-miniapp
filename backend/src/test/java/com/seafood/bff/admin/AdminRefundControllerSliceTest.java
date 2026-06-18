package com.seafood.bff.admin;

import com.seafood.order.api.dto.RefundResponse;
import com.seafood.order.application.OrderService;
import com.seafood.shared.error.NotFoundException;
import com.seafood.shared.security.AdminRateLimiter;
import com.seafood.shared.security.JwtTokenProvider;
import com.seafood.shared.security.Role;
import com.seafood.shared.security.SecurityHeadersProperties;
import com.seafood.shared.security.UserPrincipal;
import com.seafood.testsupport.builders.RefundBuilder;
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

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@WebMvcTest(AdminRefundController.class)
@EnableConfigurationProperties(SecurityHeadersProperties.class)
@Import(AdminRefundControllerSliceTest.MethodSecurityConfig.class)
class AdminRefundControllerSliceTest {

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
    @MockitoBean OrderService orderService;
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

    private static RefundResponse sampleRefund(String id) {
        var refund = RefundBuilder.aRefund().withId(id).build();
        return RefundResponse.from(refund);
    }

    @Test
    void listByStatus_asAdmin_returnsPagedRefunds() {
        Page<RefundResponse> page = new PageImpl<>(
            java.util.List.of(sampleRefund("r-1")),
            PageRequest.of(0, 20), 1);
        when(orderService.listRefunds(eq("REQUESTED"), any(Pageable.class))).thenReturn(page);

        mvc.get().uri("/api/admin/refunds?status=REQUESTED")
            .exchange()
            .assertThat()
            .hasStatusOk()
            .bodyJson()
            .hasPathSatisfying("$.content[0].id", v -> v.assertThat().isEqualTo("r-1"));
    }

    @Test
    void approve_asAdmin_returnsApprovedRefund() {
        when(orderService.approveRefund("r-1")).thenReturn(sampleRefund("r-1"));

        mvc.post().uri("/api/admin/refunds/r-1/approve")
            .exchange()
            .assertThat()
            .hasStatusOk()
            .bodyJson()
            .hasPathSatisfying("$.id", v -> v.assertThat().isEqualTo("r-1"));
    }

    @Test
    void approve_notFound_returns404() {
        when(orderService.approveRefund("missing"))
            .thenThrow(new NotFoundException("退款不存在"));

        mvc.post().uri("/api/admin/refunds/missing/approve")
            .exchange()
            .assertThat()
            .hasStatus(404);
    }

    @Test
    void approve_asCustomer_returns403() {
        SecurityContextHolder.clearContext();
        SecurityContext ctx = SecurityContextHolder.createEmptyContext();
        ctx.setAuthentication(authOf("u-1", Role.CUSTOMER));
        SecurityContextHolder.setContext(ctx);

        mvc.post().uri("/api/admin/refunds/r-1/approve")
            .exchange()
            .assertThat()
            .hasStatus(403);
    }
}
