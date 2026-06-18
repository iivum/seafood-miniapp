package com.seafood.bff.admin;

import com.seafood.bff.admin.dto.BatchShipRequest;
import com.seafood.bff.admin.dto.BatchShipResponse;
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
import org.springframework.http.MediaType;
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

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.when;

@WebMvcTest(AdminOrderController.class)
@EnableConfigurationProperties(SecurityHeadersProperties.class)
@Import(AdminOrderControllerSliceTest.MethodSecurityConfig.class)
class AdminOrderControllerSliceTest {

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
        // AdminRateLimitFilter runs BEFORE @PreAuthorize — stub grant() so
        // non-rate-limit assertions aren't blocked by NPE on Decision.permitted().
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
    void batchShip_asAdmin_returnsBatchResponse() {
        // AdminRateLimitFilter calls tryAcquire() before passing through; mock permits.
        when(adminRateLimiter.tryAcquire(any())).thenReturn(AdminRateLimiter.Decision.grant());

        var resp = new BatchShipResponse(List.of("o-1", "o-2"), List.of(), 2, 2, 0);
        when(orderService.batchShip(any(), any(), any())).thenReturn(resp);

        mvc.post().uri("/api/admin/orders/batch-ship")
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"orderIds\":[\"o-1\",\"o-2\"],\"carrier\":\"SF\",\"trackingNumber\":\"TRK-1\"}")
            .exchange()
            .assertThat()
            .hasStatusOk()
            .bodyJson()
            .hasPathSatisfying("$.successCount", v -> v.assertThat().isEqualTo(2));
    }

    @Test
    void exportCsv_asAdmin_returnsCsvString() {
        when(adminRateLimiter.tryAcquire(any())).thenReturn(AdminRateLimiter.Decision.grant());
        when(orderService.exportRecentOrdersAsCsv(anyInt())).thenReturn("﻿orderId,userId\n");

        mvc.get().uri("/api/admin/orders/export")
            .exchange()
            .assertThat()
            .hasStatusOk();
    }

    @Test
    void exportCsv_asCustomer_returns403() {
        SecurityContextHolder.clearContext();
        SecurityContext ctx = SecurityContextHolder.createEmptyContext();
        ctx.setAuthentication(authOf("u-1", Role.CUSTOMER));
        SecurityContextHolder.setContext(ctx);

        mvc.get().uri("/api/admin/orders/export")
            .exchange()
            .assertThat()
            .hasStatus(403);
    }
}
