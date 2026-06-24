package com.seafood.featureflag.api;

import com.seafood.featureflag.application.ClientFlagResponse;
import com.seafood.featureflag.application.FeatureFlagService;
import com.seafood.shared.security.AdminRateLimiter;
import com.seafood.shared.security.JwtTokenProvider;
import com.seafood.shared.security.SecurityHeadersProperties;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.http.ResponseEntity;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import com.seafood.shared.security.AdminRateLimiter;
import org.junit.jupiter.api.BeforeEach;

/**
 * FeatureFlagController 公共端点切片测试（无鉴权，任意用户可访问）。
 */
@WebMvcTest(FeatureFlagController.class)
@EnableConfigurationProperties(SecurityHeadersProperties.class)
@Import(FeatureFlagControllerTest.MethodSecurityConfig.class)
class FeatureFlagControllerTest {

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
    void setUpRateLimiter() {
        when(adminRateLimiter.tryAcquire(any())).thenReturn(AdminRateLimiter.Decision.grant());
    }

    @Test
    void getClientFlags_returns200_withFlagList() {
        when(featureFlagService.listClientFlags()).thenReturn(List.of(
                new ClientFlagResponse("new-ui", true),
                new ClientFlagResponse("dark-mode", false)
        ));

        mvc.get().uri("/api/featureflags")
                .exchange()
                .assertThat()
                .hasStatusOk()
                .bodyJson()
                .hasPathSatisfying("$[0].flagKey", v -> v.assertThat().isEqualTo("new-ui"))
                .hasPathSatisfying("$[0].enabled", v -> v.assertThat().isEqualTo(true))
                .hasPathSatisfying("$[1].flagKey", v -> v.assertThat().isEqualTo("dark-mode"));
    }

    @Test
    void getClientFlags_notRequireAuthentication() {
        // 未登录（无 SecurityContext 设置）也应返回 200
        when(featureFlagService.listClientFlags()).thenReturn(List.of());

        mvc.get().uri("/api/featureflags")
                .exchange()
                .assertThat()
                .hasStatusOk();
    }
}
