package com.seafood.gateway;

import com.seafood.common.security.JwtUtil;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.security.SignatureException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.List;

/**
 * Global authentication filter for admin routes.
 * 
 * Applies JWT verification to all /api/admin/** routes.
 * Extracts user info from valid JWT and adds headers for downstream services.
 */
@Component
public class AdminAuthenticationGlobalFilter implements GlobalFilter, Ordered {

    private static final Logger log = LoggerFactory.getLogger(AdminAuthenticationGlobalFilter.class);

    private final JwtUtil jwtUtil;

    // Routes that require admin authentication
    private static final List<String> PROTECTED_ADMIN_ROUTES = List.of(
            "/api/admin/"
    );

    // Routes that are publicly accessible (no auth required)
    private static final List<String> PUBLIC_ROUTES = List.of(
            "/api/auth/",
            "/api/products/",
            "/api/orders/",
            "/api/cart/",
            "/auth/",
            "/actuator/",
            "/swagger-ui/",
            "/v3/api-docs/"
    );

    public AdminAuthenticationGlobalFilter(JwtUtil jwtUtil) {
        this.jwtUtil = jwtUtil;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        String path = exchange.getRequest().getPath().value();

        // Skip public routes
        if (isPublicRoute(path)) {
            return chain.filter(exchange);
        }

        // Check if admin route requires authentication
        if (requiresAdminAuth(path)) {
            return authenticateAdminRequest(exchange, chain);
        }

        return chain.filter(exchange);
    }

    private boolean isPublicRoute(String path) {
        return PUBLIC_ROUTES.stream().anyMatch(path::startsWith);
    }

    private boolean requiresAdminAuth(String path) {
        return PROTECTED_ADMIN_ROUTES.stream().anyMatch(path::startsWith);
    }

    private Mono<Void> authenticateAdminRequest(ServerWebExchange exchange, GatewayFilterChain chain) {
        String authHeader = exchange.getRequest().getHeaders().getFirst(HttpHeaders.AUTHORIZATION);
        
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            log.debug("Missing or invalid authorization header for admin path: {}", exchange.getRequest().getPath());
            return writeErrorResponse(exchange, HttpStatus.UNAUTHORIZED, "Missing or invalid authorization header");
        }

        String token = authHeader.substring(7);
        try {
            String userId = jwtUtil.extractUserId(token);
            String role = jwtUtil.extractRole(token);

            // Check if user has admin role
            if (!"ADMIN".equalsIgnoreCase(role)) {
                log.warn("Non-admin user attempted to access admin route: {} with role: {}", 
                        exchange.getRequest().getPath(), role);
                return writeErrorResponse(exchange, HttpStatus.FORBIDDEN, "Access denied. Admin role required.");
            }

            // Add user info headers to request for downstream services
            ServerHttpRequest mutatedRequest = exchange.getRequest().mutate()
                    .header("X-User-Id", userId)
                    .header("X-User-Role", role != null ? role : "USER")
                    .build();

            log.debug("Admin authenticated: userId={}, role={}", userId, role);
            return chain.filter(exchange.mutate().request(mutatedRequest).build());
            
        } catch (ExpiredJwtException e) {
            log.warn("JWT expired for admin route: {}", e.getMessage());
            return writeErrorResponse(exchange, HttpStatus.UNAUTHORIZED, "Token expired");
        } catch (SignatureException e) {
            log.warn("JWT signature invalid: {}", e.getMessage());
            return writeErrorResponse(exchange, HttpStatus.UNAUTHORIZED, "Invalid token signature");
        } catch (JwtException e) {
            log.warn("JWT parsing failed: {}", e.getMessage());
            return writeErrorResponse(exchange, HttpStatus.UNAUTHORIZED, "Invalid token");
        } catch (Exception e) {
            log.error("Unexpected error during JWT authentication", e);
            return writeErrorResponse(exchange, HttpStatus.UNAUTHORIZED, "Authentication error");
        }
    }

    private Mono<Void> writeErrorResponse(ServerWebExchange exchange, HttpStatus status, String message) {
        exchange.getResponse().setStatusCode(status);
        exchange.getResponse().getHeaders().add("Content-Type", "application/json");
        String body = String.format("{\"success\":false,\"error\":\"%s\",\"code\":\"AUTH_ERROR\"}", message);
        return exchange.getResponse().setComplete();
    }

    @Override
    public int getOrder() {
        // Run this filter early in the chain to reject unauthorized requests quickly
        return Ordered.HIGHEST_PRECEDENCE + 100;
    }
}
