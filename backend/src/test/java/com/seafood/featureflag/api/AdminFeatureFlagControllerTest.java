package com.seafood.featureflag.api;

import com.seafood.featureflag.api.dto.UpdateFlagRequest;
import com.seafood.featureflag.application.FeatureFlagAuditResponse;
import com.seafood.featureflag.application.FeatureFlagResponse;
import com.seafood.featureflag.application.FeatureFlagService;
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
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
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

import java.time.Instant;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * AdminFeatureFlagController 切片测试（ADMIN-only 端点）。
 */
@WebMvcTest(AdminFeatureFlagController.class)
@EnableConfigurationProperties(SecurityHeadersProperties.class)
@Import(AdminFeatureFlagControllerTest.MethodSecurityConfig.class)
class AdminFeatureFlagControllerTest {

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

    private static final com.fasterxml.jackson.databind.ObjectMapper MAPPER =
            new com.fasterxml.jackson.databind.ObjectMapper();

    @Autowired
    MockMvcTester mvc;

    @MockitoBean
    FeatureFlagService featureFlagService;

    @MockitoBean
    AdminRateLimiter adminRateLimiter;

    @MockitoBean
    JwtTokenProvider jwtTokenProvider;

    @MockitoBean
    com.seafood.user.application.TokenRevocationService tokenRevocationService;

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

    @Test
    void listFlags_returns200_withAdminRole() {
        var pageable = PageRequest.of(0, 10);
        FeatureFlagResponse flag = flagResponse("new-ui", true);
        when(featureFlagService.listAll(any())).thenReturn(new PageImpl<>(List.of(flag), pageable, 1));

        mvc.get().uri("/api/admin/feature-flags")
                .exchange()
                .assertThat()
                .hasStatusOk()
                .bodyJson()
                .hasPathSatisfying("$.content[0].flagKey", v -> v.assertThat().isEqualTo("new-ui"));
    }

    @Test
    void listFlags_returns403_withoutAdminRole() {
        SecurityContextHolder.clearContext();
        SecurityContext ctx = SecurityContextHolder.createEmptyContext();
        ctx.setAuthentication(authOf("user-1", Role.CUSTOMER));
        SecurityContextHolder.setContext(ctx);

        mvc.get().uri("/api/admin/feature-flags")
                .exchange()
                .assertThat()
                .hasStatus(403);
    }

    @Test
    void enableFlag_returns200_andWritesAudit() throws Exception {
        UpdateFlagRequest req = new UpdateFlagRequest(true, null, null, null);

        mvc.put().uri("/api/admin/feature-flags/new-ui")
                .contentType(MediaType.APPLICATION_JSON)
                .content(MAPPER.writeValueAsString(req))
                .exchange()
                .assertThat()
                .hasStatusOk();

        verify(featureFlagService).enable(eq("new-ui"), any());
    }

    @Test
    void disableFlag_returns200_andWritesAudit() throws Exception {
        UpdateFlagRequest req = new UpdateFlagRequest(false, null, null, null);

        mvc.put().uri("/api/admin/feature-flags/new-ui")
                .contentType(MediaType.APPLICATION_JSON)
                .content(MAPPER.writeValueAsString(req))
                .exchange()
                .assertThat()
                .hasStatusOk();

        verify(featureFlagService).disable(eq("new-ui"), any());
    }

    @Test
    void updateRollout_returns400_whenPercentageOutOfRange() throws Exception {
        // rolloutPercentage = 150 超出 @Max(100) 校验
        UpdateFlagRequest req = new UpdateFlagRequest(null, 150, null, null);

        mvc.put().uri("/api/admin/feature-flags/new-ui")
                .contentType(MediaType.APPLICATION_JSON)
                .content(MAPPER.writeValueAsString(req))
                .exchange()
                .assertThat()
                .hasStatus(400);
    }

    @Test
    void getAuditLog_returns200_paged() {
        var pageable = PageRequest.of(0, 10);
        FeatureFlagAuditResponse audit = new FeatureFlagAuditResponse(
                "new-ui", "ENABLE", null, null, "admin-1", Instant.now());
        when(featureFlagService.getAuditLog(eq("new-ui"), any()))
                .thenReturn(new PageImpl<>(List.of(audit), pageable, 1));

        mvc.get().uri("/api/admin/feature-flags/new-ui/audit")
                .exchange()
                .assertThat()
                .hasStatusOk()
                .bodyJson()
                .hasPathSatisfying("$.totalElements", v -> v.assertThat().isEqualTo(1));
    }

    // ========== 工具方法 ==========

    private static Authentication authOf(String id, Role role) {
        UserPrincipal principal = new UserPrincipal(id, role);
        return new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities());
    }

    private FeatureFlagResponse flagResponse(String key, boolean enabled) {
        return new FeatureFlagResponse(key, enabled, 100, List.of(), null,
                "desc", "admin", Instant.now(), Instant.now());
    }
}
