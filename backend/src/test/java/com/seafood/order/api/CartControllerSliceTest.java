package com.seafood.order.api;

import com.seafood.order.api.dto.CartResponse;
import com.seafood.order.application.CartService;
import com.seafood.testsupport.contract.OpenApiContractAssert;
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
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.assertj.MockMvcTester;

import java.time.Instant;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * CartController @WebMvcTest slice — 2 cases.
 *
 * <p>CartController has no method-level @PreAuthorize; security is enforced by
 * SecurityConfig's URL filter {@code .requestMatchers("/api/cart/**").authenticated()}.
 * URL-filter-only auth is hard to exercise in @WebMvcTest slice (no HttpSecurity
 * bean from auto-config + SecurityConfig import conflict). The unauth case is
 * therefore covered by SecurityHeadersFilterTest / SecurityConfig integration
 * tests, not here.
 */
@WebMvcTest(CartController.class)
@EnableConfigurationProperties(SecurityHeadersProperties.class)
class CartControllerSliceTest {

    @Autowired MockMvcTester mvc;
    @MockitoBean CartService cartService;
    @MockitoBean AdminRateLimiter adminRateLimiter;
    @MockitoBean JwtTokenProvider jwtTokenProvider;
    @MockitoBean com.seafood.user.application.TokenRevocationService tokenRevocationService;

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
    void get_returnsCurrentUserCart() {
        CartResponse stub = new CartResponse("u-1", List.of(), Instant.parse("2026-06-19T00:00:00Z"));
        when(cartService.get(any())).thenReturn(stub);

        var result = mvc.get().uri("/api/cart").exchange();
        result.assertThat()
            .hasStatusOk()
            .bodyJson()
            .hasPathSatisfying("$.userId", v -> v.assertThat().isEqualTo("u-1"));
        OpenApiContractAssert.assertGetConformsToContract("/api/cart", result);
    }

    @Test
    void clear_returns204() {
        mvc.delete().uri("/api/cart")
            .exchange()
            .assertThat()
            .hasStatus(204);
    }
}
