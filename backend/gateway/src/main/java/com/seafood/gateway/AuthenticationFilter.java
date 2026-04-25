package com.seafood.gateway;

import com.seafood.common.security.JwtUtil;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.security.SignatureException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cloud.gateway.filter.GatewayFilter;
import org.springframework.cloud.gateway.filter.factory.AbstractGatewayFilterFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.List;

/**
 * Authentication filter for JWT token verification.
 * 
 * For /api/admin/** routes, extracts user info from JWT and adds headers:
 * - X-User-Id: the authenticated user's ID
 * - X-User-Role: the authenticated user's role (for authorization)
 */
@Component
public class AuthenticationFilter extends AbstractGatewayFilterFactory<AuthenticationFilter.Config> {

    private static final Logger log = LoggerFactory.getLogger(AuthenticationFilter.class);

    private final JwtUtil jwtUtil;

    // Routes that require authentication
    private static final List<String> PROTECTED_ROUTES = List.of(
            "/api/admin/"
    );

    // Routes that are publicly accessible
    private static final List<String> PUBLIC_ROUTES = List.of(
            "/api/auth/",
            "/api/products/",
            "/api/orders/",
            "/api/cart/",
            "/auth/"
    );

    public AuthenticationFilter(JwtUtil jwtUtil) {
        super(Config.class);
        this.jwtUtil = jwtUtil;
    }

    @Override
    public GatewayFilter apply(Config config) {
        return (exchange, chain) -> {
            ServerHttpRequest request = exchange.getRequest();
            String path = request.getPath().value();

            // Skip authentication for public routes
            if (isPublicRoute(path)) {
                return chain.filter(exchange);
            }

            // Check if route requires authentication
            if (!requiresAuthentication(path)) {
                return chain.filter(exchange);
            }

            String authHeader = request.getHeaders().getFirst(HttpHeaders.AUTHORIZATION);
            if (authHeader == null || !authHeader.startsWith("Bearer ")) {
                log.debug("Missing or invalid authorization header for path: {}", path);
                return onError(exchange, "Missing or invalid authorization header", HttpStatus.UNAUTHORIZED);
            }

            String token = authHeader.substring(7);
            try {
                String userId = jwtUtil.extractUserId(token);
                String role = jwtUtil.extractRole(token);

                // Check if user has admin role for admin routes
                if (path.startsWith("/api/admin/") && !"ADMIN".equalsIgnoreCase(role)) {
                    log.warn("Non-admin user attempted to access admin route: {}", path);
                    return onError(exchange, "Access denied. Admin role required.", HttpStatus.FORBIDDEN);
                }

                // Add user info headers to request
                ServerHttpRequest mutatedRequest = request.mutate()
                        .header("X-User-Id", userId)
                        .header("X-User-Role", role != null ? role : "USER")
                        .build();

                return chain.filter(exchange.mutate().request(mutatedRequest).build());
            } catch (ExpiredJwtException e) {
                log.warn("JWT expired: {}", e.getMessage());
                return onError(exchange, "Token expired", HttpStatus.UNAUTHORIZED);
            } catch (SignatureException e) {
                log.warn("JWT signature invalid: {}", e.getMessage());
                return onError(exchange, "Invalid token", HttpStatus.UNAUTHORIZED);
            } catch (JwtException e) {
                log.warn("JWT parsing failed: {}", e.getMessage());
                return onError(exchange, "Invalid token", HttpStatus.UNAUTHORIZED);
            } catch (Exception e) {
                log.error("Unexpected error during JWT authentication", e);
                return onError(exchange, "Authentication error", HttpStatus.UNAUTHORIZED);
            }
        };
    }

    private boolean isPublicRoute(String path) {
        return PUBLIC_ROUTES.stream().anyMatch(path::startsWith);
    }

    private boolean requiresAuthentication(String path) {
        return PROTECTED_ROUTES.stream().anyMatch(path::startsWith);
    }

    private Mono<Void> onError(ServerWebExchange exchange, String err, HttpStatus httpStatus) {
        log.debug("Authentication error: {} — responding with {}", err, httpStatus);
        exchange.getResponse().setStatusCode(httpStatus);
        exchange.getResponse().getHeaders().add("Content-Type", "application/json");
        String body = String.format("{\"success\":false,\"error\":\"%s\",\"code\":\"AUTH_ERROR\"}", err);
        return exchange.getResponse().setComplete();
    }

    public static class Config {
    }
}
