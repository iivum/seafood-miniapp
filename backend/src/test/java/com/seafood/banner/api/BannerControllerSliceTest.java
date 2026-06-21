package com.seafood.banner.api;

import com.seafood.banner.api.dto.BannerResponse;
import com.seafood.banner.application.BannerService;
import com.seafood.banner.domain.BannerStatus;
import com.seafood.banner.domain.BannerTone;
import com.seafood.shared.error.NotFoundException;
import com.seafood.shared.security.AdminRateLimiter;
import com.seafood.shared.security.JwtTokenProvider;
import com.seafood.shared.security.Role;
import com.seafood.shared.security.SecurityHeadersProperties;
import com.seafood.shared.security.UserPrincipal;
import org.junit.jupiter.api.AfterEach;
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

import java.time.Instant;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@WebMvcTest(BannerController.class)
@EnableConfigurationProperties(SecurityHeadersProperties.class)
@Import(BannerControllerSliceTest.MethodSecurityConfig.class)
class BannerControllerSliceTest {

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
    @MockitoBean BannerService bannerService;
    @MockitoBean AdminRateLimiter adminRateLimiter;
    @MockitoBean JwtTokenProvider jwtTokenProvider;
    @MockitoBean com.seafood.user.application.TokenRevocationService tokenRevocationService;

    @AfterEach
    void clearAuth() {
        SecurityContextHolder.clearContext();
    }

    private static void authenticate(String id, Role role) {
        UserPrincipal principal = new UserPrincipal(id, role);
        Authentication auth = new UsernamePasswordAuthenticationToken(
                principal, null, principal.getAuthorities());
        SecurityContext ctx = SecurityContextHolder.createEmptyContext();
        ctx.setAuthentication(auth);
        SecurityContextHolder.setContext(ctx);
    }

    private static BannerResponse resp(String id, BannerStatus status) {
        Instant now = Instant.parse("2026-06-20T00:00:00Z");
        return new BannerResponse(id, BannerTone.ACCENT, "🦞", "波龙季", "鲜活到岸",
                "p-1", 0, status, now, now);
    }

    @Test
    void list_public_returnsActiveBanners() {
        when(bannerService.listActive()).thenReturn(List.of(resp("b1", BannerStatus.ACTIVE)));

        mvc.get().uri("/api/banners").exchange()
                .assertThat().hasStatusOk()
                .bodyJson().hasPath("$[0].id");
    }

    @Test
    void get_notFound_returns404() {
        when(bannerService.get("missing")).thenThrow(new NotFoundException("banner 不存在"));

        mvc.get().uri("/api/banners/missing").exchange()
                .assertThat().hasStatus(404)
                .bodyJson().hasPath("$.code");
    }

    @Test
    void listAll_asCustomer_returns403() {
        authenticate("u-1", Role.CUSTOMER);

        mvc.get().uri("/api/banners/all").exchange()
                .assertThat().hasStatus(403);
    }

    @Test
    void listAll_asAdmin_returnsAll() {
        authenticate("admin-1", Role.ADMIN);
        when(bannerService.listAll())
                .thenReturn(List.of(resp("b1", BannerStatus.ACTIVE), resp("b2", BannerStatus.INACTIVE)));

        mvc.get().uri("/api/banners/all").exchange()
                .assertThat().hasStatusOk()
                .bodyJson().hasPath("$[1].status");
    }

    @Test
    void create_asCustomer_returns403() {
        authenticate("u-1", Role.CUSTOMER);

        mvc.post().uri("/api/banners")
                .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                .content("{\"tone\":\"ACCENT\",\"emoji\":\"🦞\",\"title\":\"t\",\"subtitle\":\"s\",\"sortOrder\":0,\"active\":true}")
                .exchange()
                .assertThat().hasStatus(403);
    }

    @Test
    void create_asAdmin_returns201() {
        authenticate("admin-1", Role.ADMIN);
        when(bannerService.create(any())).thenReturn(resp("b-new", BannerStatus.ACTIVE));

        mvc.post().uri("/api/banners")
                .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                .content("{\"tone\":\"ACCENT\",\"emoji\":\"🦞\",\"title\":\"波龙季\",\"subtitle\":\"鲜活到岸\",\"targetProductId\":\"p-1\",\"sortOrder\":0,\"active\":true}")
                .exchange()
                .assertThat().hasStatus(201)
                .bodyJson().hasPath("$.id");
    }

    @Test
    void delete_asAdmin_returns204() {
        authenticate("admin-1", Role.ADMIN);

        mvc.delete().uri("/api/banners/b1").exchange()
                .assertThat().hasStatus(204);
    }
}
