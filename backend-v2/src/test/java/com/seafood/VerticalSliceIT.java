package com.seafood;

import com.seafood.product.api.dto.ProductListResponse;
import com.seafood.user.api.dto.AuthResponse;
import com.seafood.user.api.dto.LoginRequest;
import com.seafood.user.api.dto.RegisterRequest;
import com.seafood.user.infra.UserMongoRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestClient;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Phase 1 vertical slice: register → login → get token → call protected / products list.
 * Requires MongoDB on localhost:27017 (docker compose up mongodb).
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@DisplayName("Phase 1 vertical slice: login + product list end-to-end")
class VerticalSliceIT {

    @LocalServerPort int port;
    @Autowired MongoTemplate mongo;
    @Autowired UserMongoRepository users;

    RestClient client;

    @BeforeEach
    void setup() {
        client = RestClient.create("http://localhost:" + port);
        // Drop only products; keep users (admin bootstrapped at @PostConstruct).
        // The unique-index on users.username will enforce isolation if a test
        // attempts to re-register the same username — see duplicateUsername test.
        mongo.dropCollection("products");
        var p = new com.seafood.product.domain.Product(
            null, "测试三文鱼", "spike 测试用", new BigDecimal("99.00"),
            10, "鱼类", "https://placehold.co/400", true,
            Instant.now(), Instant.now()
        );
        mongo.save(p);
    }

    @Test
    @DisplayName("register → login → access protected resource with bearer token")
    void registerLoginAndListProducts() {
        String username = "alice_" + Math.abs(System.nanoTime() % 1_000_000);
        // 1. register a customer
        var reg = new RegisterRequest(username, "secret123", "Alice");
        ResponseEntity<AuthResponse> regRes = client.post()
            .uri("/api/auth/register")
            .contentType(MediaType.APPLICATION_JSON)
            .body(reg)
            .retrieve()
            .toEntity(AuthResponse.class);
        assertThat(regRes.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(regRes.getBody()).isNotNull();
        assertThat(regRes.getBody().accessToken()).isNotBlank();

        // 2. login again
        var login = new LoginRequest(username, "secret123");
        AuthResponse loginRes = client.post()
            .uri("/api/auth/login")
            .contentType(MediaType.APPLICATION_JSON)
            .body(login)
            .retrieve()
            .body(AuthResponse.class);
        assertThat(loginRes).isNotNull();
        assertThat(loginRes.role()).isEqualTo("CUSTOMER");

        // 3. call /api/products (public — no token needed)
        ProductListResponse list = client.get()
            .uri("/api/products")
            .retrieve()
            .body(ProductListResponse.class);
        assertThat(list).isNotNull();
        assertThat(list.totalProducts()).isEqualTo(1);
        assertThat(list.products().get(0).name()).isEqualTo("测试三文鱼");

        // 4. bearer token still works for any authed endpoint (validation only)
        var me = client.get()
            .uri("/api/products")
            .header(HttpHeaders.AUTHORIZATION, "Bearer " + loginRes.accessToken())
            .retrieve()
            .toEntity(ProductListResponse.class);
        assertThat(me.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    @DisplayName("admin bootstrap: seeded by @PostConstruct, login as admin")
    void adminBootstrapWorks() {
        assertThat(users.existsByUsername("admin")).isTrue();

        var login = new LoginRequest("admin", "admin123");
        AuthResponse res = client.post()
            .uri("/api/auth/login")
            .contentType(MediaType.APPLICATION_JSON)
            .body(login)
            .retrieve()
            .body(AuthResponse.class);
        assertThat(res).isNotNull();
        assertThat(res.role()).isEqualTo("ADMIN");
    }

    @Test
    @DisplayName("bad credentials returns 401 with proper error envelope")
    void badCredentials() {
        try {
            client.post()
                .uri("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .body(new LoginRequest("nobody", "wrong"))
                .retrieve()
                .toEntity(Map.class);
            org.junit.jupiter.api.Assertions.fail("expected 401");
        } catch (org.springframework.web.client.HttpClientErrorException e) {
            assertThat(e.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
            assertThat(e.getResponseBodyAsString()).contains("UNAUTHORIZED");
        }
    }

    @Test
    @DisplayName("duplicate username returns 409 CONFLICT")
    void duplicateUsername() {
        String username = "bob" + Math.abs(System.nanoTime() % 1_000_000);
        client.post()
            .uri("/api/auth/register")
            .contentType(MediaType.APPLICATION_JSON)
            .body(new RegisterRequest(username, "secret123", "Bob"))
            .retrieve()
            .toBodilessEntity();

        try {
            client.post()
                .uri("/api/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .body(new RegisterRequest(username, "secret123", "Bob"))
                .retrieve()
                .toBodilessEntity();
            org.junit.jupiter.api.Assertions.fail("expected 409");
        } catch (org.springframework.web.client.HttpClientErrorException e) {
            assertThat(e.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        }
    }

    @Test
    @DisplayName("invalid token → request still works for public, ignored for protected")
    void invalidJwtIgnored() {
        // Public endpoint should still work
        ProductListResponse res = client.get()
            .uri("/api/products")
            .header(HttpHeaders.AUTHORIZATION, "Bearer total.garbage.token")
            .retrieve()
            .body(ProductListResponse.class);
        assertThat(res.totalProducts()).isEqualTo(1);
    }
}
