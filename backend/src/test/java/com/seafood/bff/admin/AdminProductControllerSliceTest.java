package com.seafood.bff.admin;

import com.seafood.product.api.dto.ProductResponse;
import com.seafood.product.application.ProductService;
import com.seafood.product.domain.ProductStatus;
import com.seafood.shared.error.NotFoundException;
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

import java.math.BigDecimal;
import java.time.Instant;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@WebMvcTest(AdminProductController.class)
@EnableConfigurationProperties(SecurityHeadersProperties.class)
@Import(AdminProductControllerSliceTest.MethodSecurityConfig.class)
class AdminProductControllerSliceTest {

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
        return new ProductResponse(id, "测试商品", "默认描述", new BigDecimal("99.00"),
            100, "鱼类", "https://img.test/" + id + ".jpg", ProductStatus.ACTIVE,
            Instant.parse("2026-06-19T00:00:00Z"), Instant.parse("2026-06-19T00:00:00Z"));
    }

    @Test
    void duplicate_asAdmin_returns201WithBody() {
        when(productService.duplicate("p-1")).thenReturn(sampleProduct("p-1"));

        mvc.post().uri("/api/admin/products/p-1/duplicate")
            .exchange()
            .assertThat()
            .hasStatus(201)
            .bodyJson()
            .hasPathSatisfying("$.id", v -> v.assertThat().isEqualTo("p-1"));
    }

    @Test
    void duplicate_asCustomer_returns403() {
        SecurityContextHolder.clearContext();
        SecurityContext ctx = SecurityContextHolder.createEmptyContext();
        ctx.setAuthentication(authOf("u-1", Role.CUSTOMER));
        SecurityContextHolder.setContext(ctx);

        mvc.post().uri("/api/admin/products/p-1/duplicate")
            .exchange()
            .assertThat()
            .hasStatus(403);
    }

    @Test
    void duplicate_notFound_returns404() {
        when(productService.duplicate("missing"))
            .thenThrow(new NotFoundException("商品不存在"));

        mvc.post().uri("/api/admin/products/missing/duplicate")
            .exchange()
            .assertThat()
            .hasStatus(404);
    }

    @Test
    void export_asAdmin_returnsCsvBytes() {
        when(productService.exportRecentProductsAsCsv()).thenReturn("id,name\np-1,三文鱼\n");

        mvc.get().uri("/api/admin/products/export")
            .exchange()
            .assertThat()
            .hasStatusOk()
            .hasHeader("Content-Type", "text/csv;charset=UTF-8");
    }

    @Test
    void batchStatus_success_returnsSuccessCount() {
        mvc.post().uri("/api/admin/products/batch-status")
            .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
            .content("{\"ids\":[\"p-1\",\"p-2\"],\"status\":\"ACTIVE\"}")
            .exchange()
            .assertThat()
            .hasStatusOk()
            .bodyJson()
            .hasPathSatisfying("$.successCount", v -> v.assertThat().isEqualTo(2));
    }

    @Test
    void batchStatus_emptyIds_returns409() {
        mvc.post().uri("/api/admin/products/batch-status")
            .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
            .content("{\"ids\":[],\"status\":\"ACTIVE\"}")
            .exchange()
            .assertThat()
            .hasStatus(409);
    }
}
