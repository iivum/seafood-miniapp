package com.seafood;

import com.seafood.order.api.dto.CartResponse;
import com.seafood.order.api.dto.OrderResponse;
import com.seafood.order.api.dto.OrderPageResponse;
import com.seafood.product.api.dto.CreateProductRequest;
import com.seafood.product.api.dto.ProductResponse;
import com.seafood.product.infra.ProductMongoRepository;
import com.seafood.user.api.dto.AuthResponse;
import com.seafood.user.api.dto.LoginRequest;
import com.seafood.user.api.dto.RegisterRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Phase 2: full order flow — register → seed product → add to cart → checkout → pay → cancel.
 * Requires MongoDB on localhost:27017.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@DisplayName("Order flow: cart → checkout → pay → cancel")
class OrderIT {

    @LocalServerPort int port;
    @Autowired MongoTemplate mongo;
    @Autowired ProductMongoRepository productRepo;

    RestClient client;

    String productA;
    String productB;
    String customerToken;
    String adminToken;

    @BeforeEach
    void setup() {
        client = RestClient.create("http://localhost:" + port);
        mongo.dropCollection("products");
        mongo.dropCollection("carts");
        mongo.dropCollection("orders");

        // Seed two products
        com.seafood.product.domain.Product savedA = productRepo.save(com.seafood.product.domain.Product.create(
            "三文鱼", "挪威进口", new BigDecimal("128.00"), 50, "鱼类",
            "https://x", true, java.time.Instant.now()
        ));
        com.seafood.product.domain.Product savedB = productRepo.save(com.seafood.product.domain.Product.create(
            "黑虎虾", "越南进口", new BigDecimal("89.00"), 100, "虾蟹",
            "https://x", true, java.time.Instant.now()
        ));
        productA = savedA.id();
        productB = savedB.id();

        // Register a customer + use bootstrapped admin
        String username = "buyer" + Math.abs(System.nanoTime() % 1_000_000);
        AuthResponse customer = client.post()
            .uri("/api/auth/register")
            .contentType(MediaType.APPLICATION_JSON)
            .body(new RegisterRequest(username, "secret123", "Buyer"))
            .retrieve()
            .body(AuthResponse.class);
        customerToken = customer.accessToken();

        AuthResponse admin = client.post()
            .uri("/api/auth/login")
            .contentType(MediaType.APPLICATION_JSON)
            .body(new LoginRequest("admin", "admin123"))
            .retrieve()
            .body(AuthResponse.class);
        adminToken = admin.accessToken();
    }

    private RestClient authed(String token) {
        return RestClient.builder()
            .baseUrl("http://localhost:" + port)
            .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + token)
            .build();
    }

    @Test
    @DisplayName("add 2 items → checkout → verify PENDING order with correct total")
    void checkoutCreatesPendingOrder() {
        RestClient me = authed(customerToken);

        me.post().uri("/api/cart/items")
            .contentType(MediaType.APPLICATION_JSON)
            .body(Map.of("productId", productA, "quantity", 2))
            .retrieve()
            .toBodilessEntity();

        me.post().uri("/api/cart/items")
            .contentType(MediaType.APPLICATION_JSON)
            .body(Map.of("productId", productB, "quantity", 1))
            .retrieve()
            .toBodilessEntity();

        OrderResponse order = me.post().uri("/api/orders")
            .contentType(MediaType.APPLICATION_JSON)
            .body(Map.of())
            .retrieve()
            .body(OrderResponse.class);

        // 128 * 2 + 89 * 1 = 345
        assertThat(order.totalAmount()).isEqualByComparingTo("345.00");
        assertThat(order.status()).isEqualTo("PENDING");
        assertThat(order.items()).hasSize(2);
    }

    @Test
    @DisplayName("admin marks PENDING order PAID → status flips")
    void adminMarksPaid() {
        RestClient me = authed(customerToken);
        me.post().uri("/api/cart/items")
            .contentType(MediaType.APPLICATION_JSON)
            .body(Map.of("productId", productA, "quantity", 1))
            .retrieve()
            .toBodilessEntity();
        OrderResponse order = me.post().uri("/api/orders")
            .contentType(MediaType.APPLICATION_JSON)
            .body(Map.of())
            .retrieve()
            .body(OrderResponse.class);

        RestClient admin = authed(adminToken);
        OrderResponse paid = admin.patch().uri("/api/orders/{id}/pay?paymentRef=wx_123", order.id())
            .retrieve()
            .body(OrderResponse.class);

        assertThat(paid.status()).isEqualTo("PAID");
        assertThat(paid.paymentRef()).isEqualTo("wx_123");
    }

    @Test
    @DisplayName("customer cancels PENDING order → status=CANCELLED with reason")
    void customerCanCancelPending() {
        RestClient me = authed(customerToken);
        me.post().uri("/api/cart/items")
            .contentType(MediaType.APPLICATION_JSON)
            .body(Map.of("productId", productA, "quantity", 1))
            .retrieve()
            .toBodilessEntity();
        OrderResponse order = me.post().uri("/api/orders")
            .contentType(MediaType.APPLICATION_JSON)
            .body(Map.of())
            .retrieve()
            .body(OrderResponse.class);

        OrderResponse cancelled = me.patch()
            .uri("/api/orders/{id}/cancel?reason=不要了", order.id())
            .retrieve()
            .body(OrderResponse.class);

        assertThat(cancelled.status()).isEqualTo("CANCELLED");
        assertThat(cancelled.cancelReason()).isEqualTo("不要了");
    }

    @Test
    @DisplayName("cannot cancel another user's order → 403")
    void cannotCancelOthersOrder() throws Exception {
        // Customer A creates an order
        RestClient me = authed(customerToken);
        me.post().uri("/api/cart/items")
            .contentType(MediaType.APPLICATION_JSON)
            .body(Map.of("productId", productA, "quantity", 1))
            .retrieve()
            .toBodilessEntity();
        OrderResponse order = me.post().uri("/api/orders")
            .contentType(MediaType.APPLICATION_JSON)
            .body(Map.of())
            .retrieve()
            .body(OrderResponse.class);

        // Customer B (different token)
        String buyerB = "buyer" + Math.abs((System.nanoTime() + 7) % 1_000_000);
        AuthResponse customerB = client.post()
            .uri("/api/auth/register")
            .contentType(MediaType.APPLICATION_JSON)
            .body(new RegisterRequest(buyerB, "secret123", "B"))
            .retrieve()
            .body(AuthResponse.class);
        RestClient b = authed(customerB.accessToken());

        try {
            b.patch().uri("/api/orders/{id}/cancel", order.id()).retrieve().toBodilessEntity();
            org.junit.jupiter.api.Assertions.fail("expected 403");
        } catch (HttpClientErrorException e) {
            assertThat(e.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        }
    }

    @Test
    @DisplayName("non-admin cannot create product → 403")
    void nonAdminCannotCreateProduct() {
        RestClient me = authed(customerToken);
        try {
            me.post().uri("/api/products")
                .contentType(MediaType.APPLICATION_JSON)
                .body(new CreateProductRequest("hack", "x", new BigDecimal("1.00"), 1, "x", "x", true))
                .retrieve()
                .toBodilessEntity();
            org.junit.jupiter.api.Assertions.fail("expected 403");
        } catch (HttpClientErrorException e) {
            assertThat(e.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        }
    }

    @Test
    @DisplayName("admin can create then list product")
    void adminCanCreateProduct() {
        RestClient admin = authed(adminToken);
        ProductResponse created = admin.post().uri("/api/products")
            .contentType(MediaType.APPLICATION_JSON)
            .body(new CreateProductRequest("admin 鳕鱼", "新鲜", new BigDecimal("98.00"), 80, "鱼类", "https://x", true))
            .retrieve()
            .body(ProductResponse.class);
        assertThat(created.id()).isNotBlank();
        assertThat(created.name()).isEqualTo("admin 鳕鱼");
    }

    @Test
    @DisplayName("GET /api/orders/me returns the buyer's orders")
    void listMyOrders() {
        RestClient me = authed(customerToken);
        me.post().uri("/api/cart/items")
            .contentType(MediaType.APPLICATION_JSON)
            .body(Map.of("productId", productA, "quantity", 1))
            .retrieve()
            .toBodilessEntity();
        me.post().uri("/api/orders")
            .contentType(MediaType.APPLICATION_JSON)
            .body(Map.of())
            .retrieve()
            .toBodilessEntity();

        OrderPageResponse page = me.get().uri("/api/orders/me?page=0&size=10")
            .retrieve()
            .body(OrderPageResponse.class);
        assertThat(page.orders()).hasSize(1);
        assertThat(page.total()).isEqualTo(1);
    }
}
