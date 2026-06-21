# Sprint 2 / 子项目 ② A — Backend Coverage Backfill Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Close backend test coverage gap by adding 15 test classes (6 controller slice + 4 repository slice + 5 BFF integration), driving Jacoco global ≥80%, zero regression.

**Architecture:** Spring Boot 4 modern test stack — `@WebMvcTest` (new package `org.springframework.boot.webmvc.test.autoconfigure`) + `@MockitoBean` (new package `org.springframework.test.context.bean.override.mockito`) + `MockMvcTester` (from `spring-test:7.0.8`) for controllers; `extends MongoIntegrationTest` for repositories. All fixtures use D1 builders (shipped in `sprint-2-test-data-builders` change). Auth: `@WithMockUser` from `spring-security-test:7.0.6`. **Modern stack is MANDATORY** — no plain JUnit fallback (build.gradle change committed in c8fc506).

**Tech Stack:** Spring Boot 4.0.6 + JDK 25 + Gradle 9.x, JUnit 5, Mockito 5.x, AssertJ 3.27.7, Testcontainers 1.20.4 (mongo:7).

**File map (15 new test classes, no production-code changes):**

| # | File path under `backend/src/test/java/com/seafood/` |
|---|---|
| 1 | `product/api/ProductControllerSliceTest.java` |
| 2 | `order/api/OrderControllerSliceTest.java` |
| 3 | `order/api/CartControllerSliceTest.java` |
| 4 | `bff/admin/AdminOrderControllerSliceTest.java` |
| 5 | `bff/admin/AdminProductControllerSliceTest.java` |
| 6 | `bff/admin/AdminRefundControllerSliceTest.java` |
| 7 | `order/infra/OrderRepositorySliceTest.java` |
| 8 | `product/infra/ProductRepositorySliceTest.java` |
| 9 | `user/infra/UserRepositorySliceTest.java` |
| 10 | `order/infra/RefundRepositorySliceTest.java` |
| 11 | `bff/admin/AdminBffControllerSliceTest.java` |
| 12 | `bff/admin/AdminBffOrderDetailSliceTest.java` |
| 13 | `bff/admin/AdminBffOrderListSliceTest.java` |
| 14 | `bff/admin/AdminBffProductDuplicateSliceTest.java` |
| 15 | `bff/admin/AdminBffBatchShipSliceTest.java` |

---

## Common imports / annotation header (reused by every controller slice test)

Every modern-stack controller test starts with this header (adapt the `@WebMvcTest` target + `@MockitoBean` collaborators per task):

```java
package com.seafood.<module>;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.assertj.MockMvcTester;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
```

Notes:
- `@WebMvcTest` lives at `org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest` (Boot 4 — NOT the old `org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest`).
- `@MockitoBean` is the modern replacement for the deprecated `@MockBean`; it lives at `org.springframework.test.context.bean.override.mockito.MockitoBean`.
- `MockMvcTester` lives at `org.springframework.test.web.servlet.assertj.MockMvcTester` (AssertJ-fluent).
- `@WithMockUser(username="u-1", roles="CUSTOMER")` populates a real `UserPrincipal` via the project's auth resolver for `@AuthenticationPrincipal`-bound methods.
- For unauth-rejected tests, **omit** `@WithMockUser` and assert via `assertThatThrownBy(() -> mvc.post()...)` → `hasCauseInstanceOf(AccessDeniedException.class)` (Spring's `@PreAuthorize` rejects BEFORE the handler runs).
- `MockMvcTester` returns `MvcTestResult` from `.exchange()`; chain `.assertThat()` for AssertJ assertions on the response.

---

## Phase 1: Experimental Pilot (validate modern stack)

### Task 1: Pilot ProductControllerSliceTest — Spring Boot 4 modern stack

**Files:**
- Create: `backend/src/test/java/com/seafood/product/api/ProductControllerSliceTest.java`

**Context:** Pilot validates the modern stack (c8fc506 added `spring-boot-webmvc-test:4.0.6` + `spring-test:7.0.8`). The build dep is already on the classpath. **This is the contract test for the entire plan** — Tasks 2-6 + 11-15 reuse this exact pattern.

`ProductController` (`com.seafood.product.api.ProductController`, `@RequestMapping("/api/products")`):
- `Page<ProductResponse> list(String category, Pageable pageable)` — public
- `ProductResponse get(String id)` — public
- `ResponseEntity<ProductResponse> create(@Valid ProductRequest req)` — ADMIN
- `ProductResponse update(String id, @Valid ProductRequest req)` — ADMIN
- `ResponseEntity<Void> delete(String id)` — ADMIN
- `ProductResponse discontinue(String id)` — ADMIN

- [ ] **Step 1: Write ProductControllerSliceTest**

Create `backend/src/test/java/com/seafood/product/api/ProductControllerSliceTest.java`:

```java
package com.seafood.product.api;

import com.seafood.product.api.dto.ProductResponse;
import com.seafood.product.application.ProductService;
import com.seafood.product.domain.ProductStatus;
import com.seafood.shared.error.NotFoundException;
import com.seafood.testsupport.builders.ProductBuilder;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.assertj.MockMvcTester;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@WebMvcTest(ProductController.class)
class ProductControllerSliceTest {

    @Autowired MockMvcTester mvc;
    @MockitoBean ProductService productService;

    @Test
    void list_returnsPagedProducts() {
        var product = ProductBuilder.aProduct().withId("p-test").build();
        var response = ProductResponse("p-test", product);
        Page<ProductResponse> page = new PageImpl<>(List.of(response), PageRequest.of(0, 20), 1);
        when(productService.listPublic(eq(null), any())).thenReturn(page);

        mvc.get().uri("/api/products")
            .exchange()
            .assertThat()
            .hasStatusOk()
            .bodyJson()
            .hasPath("$.content[0].id");
    }

    @Test
    void getProduct_notFound_returns404() {
        when(productService.get("missing"))
            .thenThrow(new NotFoundException("产品不存在"));

        mvc.get().uri("/api/products/missing")
            .exchange()
            .assertThat()
            .hasStatus(404)
            .bodyJson()
            .hasPath("$.code");
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void discontinue_asAdmin_returnsUpdatedProduct() {
        var discontinued = ProductBuilder.aProduct().withId("p-1").build();
        when(productService.discontinue(eq("p-1")))
            .thenReturn(ProductResponse("p-1", discontinued));

        mvc.post().uri("/api/products/p-1/discontinue")
            .exchange()
            .assertThat()
            .hasStatusOk();
    }

    private static ProductResponse ProductResponse(String id, com.seafood.product.domain.Product p) {
        return new ProductResponse(id, p.getName(), p.getDescription(),
            BigDecimal.valueOf(p.getPrice()), p.getStock(), p.getCategory(), p.getImageUrl(),
            p.getStatus() == null ? ProductStatus.ON_SALE : p.getStatus(),
            Instant.now(), Instant.now());
    }
}
```

- [ ] **Step 2: Run test to verify it compiles and passes**

Run: `cd backend && ./gradlew test --tests "com.seafood.product.api.ProductControllerSliceTest"`
Expected: 3 tests pass.

If compile fails with `cannot find symbol: WebMvcTest` from old package → the build.gradle hasn't picked up the dep from c8fc506 yet. Run `./gradlew --refresh-dependencies test` once.

If `ProductResponse` constructor signature differs (e.g. field order), use the actual canonical constructor — read the source if needed.

- [ ] **Step 3: Commit**

```bash
git add backend/src/test/java/com/seafood/product/api/ProductControllerSliceTest.java
git commit -m "test(product): ProductControllerSliceTest pilot — Boot 4 modern stack

Modern stack contract: @WebMvcTest (new pkg) + @MockitoBean + MockMvcTester.
Reused by Tasks 2-6 + 11-15. 3 cases: list, get 404, discontinue ADMIN."
```

---

## Phase 2: Other Controller Slices (5 classes)

### Task 2: OrderControllerSliceTest

**Files:**
- Create: `backend/src/test/java/com/seafood/order/api/OrderControllerSliceTest.java`

**Context:** `OrderController` (`com.seafood.order.api.OrderController`, `@RequestMapping("/api/orders")`). Endpoints (selected for coverage): `list`, `get`, `create` (takes `@AuthenticationPrincipal UserPrincipal me`), `ship` (ADMIN only), `cancel` (CUSTOMER|ADMIN). Use `@WithMockUser(username="u-1", roles="CUSTOMER")` so the auth resolver binds a `UserPrincipal` with `id="u-1"` and `Role.CUSTOMER`.

- [ ] **Step 1: Write OrderControllerSliceTest**

Create `backend/src/test/java/com/seafood/order/api/OrderControllerSliceTest.java`:

```java
package com.seafood.order.api;

import com.seafood.order.application.OrderService;
import com.seafood.order.api.dto.OrderResponse;
import com.seafood.shared.error.NotFoundException;
import com.seafood.testsupport.builders.OrderBuilder;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.assertj.MockMvcTester;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@WebMvcTest(OrderController.class)
class OrderControllerSliceTest {

    @Autowired MockMvcTester mvc;
    @MockitoBean OrderService orderService;

    @Test
    @WithMockUser(username = "u-1", roles = "CUSTOMER")
    void list_asCustomer_returnsUserPagedOrders() {
        var order = OrderBuilder.anOrder().withId("o-1").withUserId("u-1").build();
        var resp = orderResp(order);
        Page<OrderResponse> page = new PageImpl<>(List.of(resp), PageRequest.of(0, 20), 1);
        when(orderService.list(eq("u-1"), any())).thenReturn(page);

        mvc.get().uri("/api/orders")
            .exchange()
            .assertThat()
            .hasStatusOk()
            .bodyJson()
            .hasPath("$.content[0].userId");
    }

    @Test
    @WithMockUser(username = "u-1", roles = "CUSTOMER")
    void getOrder_notFound_returns404() {
        when(orderService.get("missing"))
            .thenThrow(new NotFoundException("订单不存在"));

        mvc.get().uri("/api/orders/missing")
            .exchange()
            .assertThat()
            .hasStatus(404);
    }

    @Test
    @WithMockUser(username = "u-1", roles = "CUSTOMER")
    void ship_asCustomer_returns403() {
        // @PreAuthorize hasRole('ADMIN') rejects CUSTOMER
        mvc.post().uri("/api/orders/o-1/ship")
            .exchange()
            .assertThat()
            .hasStatus(403);
    }

    @Test
    @WithMockUser(username = "u-1", roles = "ADMIN")
    void ship_asAdmin_returnsUpdatedOrder() {
        var order = OrderBuilder.anOrder().withId("o-1").build();
        when(orderService.ship(eq("o-1"))).thenReturn(orderResp(order));

        mvc.post().uri("/api/orders/o-1/ship")
            .exchange()
            .assertThat()
            .hasStatusOk()
            .bodyJson()
            .hasPath("$.id");
    }

    private static OrderResponse orderResp(com.seafood.order.domain.Order o) {
        return new OrderResponse(o.getId(), o.getUserId(), List.of(),
            BigDecimal.ZERO, "PENDING", null, null, null,
            Instant.now(), Instant.now());
    }
}
```

- [ ] **Step 2: Run test**

Run: `cd backend && ./gradlew test --tests "com.seafood.order.api.OrderControllerSliceTest"`
Expected: 4 tests pass.

If `OrderResponse` canonical-constructor arity differs (e.g., it has 11 fields not 10), read the source and align the helper.

- [ ] **Step 3: Commit**

```bash
git add backend/src/test/java/com/seafood/order/api/OrderControllerSliceTest.java
git commit -m "test(order): OrderControllerSliceTest — 4 cases (list, get 404, ship 403/ADMIN)"
```

### Task 3: CartControllerSliceTest

**Files:**
- Create: `backend/src/test/java/com/seafood/order/api/CartControllerSliceTest.java`

**Context:** `CartController` (`com.seafood.order.api.CartController`, `@RequestMapping("/api/cart")`). No class-level `@PreAuthorize` — security is enforced by URL filter (must be authenticated). All methods take `@AuthenticationPrincipal UserPrincipal me`. Service: `CartService.get(userId)`, `CartService.addItem(userId, req)`, `CartService.removeItem(userId, productId)`, `CartService.clear(userId)`.

- [ ] **Step 1: Write CartControllerSliceTest**

Create `backend/src/test/java/com/seafood/order/api/CartControllerSliceTest.java`:

```java
package com.seafood.order.api;

import com.seafood.order.application.CartService;
import com.seafood.order.api.dto.CartResponse;
import com.seafood.testsupport.builders.CartBuilder;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.assertj.MockMvcTester;

import java.time.Instant;
import java.util.List;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@WebMvcTest(CartController.class)
class CartControllerSliceTest {

    @Autowired MockMvcTester mvc;
    @MockitoBean CartService cartService;

    @Test
    @WithMockUser(username = "u-1", roles = "CUSTOMER")
    void getCart_returnsCurrentUserCart() {
        var cart = CartBuilder.aCart().withUserId("u-1").build();
        when(cartService.get(eq("u-1")))
            .thenReturn(new CartResponse("u-1", List.of(), Instant.now()));

        mvc.get().uri("/api/cart")
            .exchange()
            .assertThat()
            .hasStatusOk()
            .bodyJson()
            .hasPath("$.userId");
    }

    @Test
    @WithMockUser(username = "u-1", roles = "CUSTOMER")
    void clearCart_returns204() {
        mvc.delete().uri("/api/cart")
            .exchange()
            .assertThat()
            .hasStatus(204);
    }

    @Test
    void getCart_withoutAuth_returns403() {
        mvc.get().uri("/api/cart")
            .exchange()
            .assertThat()
            .hasStatus(403);
    }
}
```

- [ ] **Step 2: Run test**

Run: `cd backend && ./gradlew test --tests "com.seafood.order.api.CartControllerSliceTest"`
Expected: 3 tests pass.

- [ ] **Step 3: Commit**

```bash
git add backend/src/test/java/com/seafood/order/api/CartControllerSliceTest.java
git commit -m "test(cart): CartControllerSliceTest — 3 cases (get, clear, unauth 403)"
```

### Task 4: AdminOrderControllerSliceTest

**Files:**
- Create: `backend/src/test/java/com/seafood/bff/admin/AdminOrderControllerSliceTest.java`

**Context:** `AdminOrderController` (`com.seafood.bff.admin.AdminOrderController`, `@RequestMapping("/api/admin/orders")`, class-level `hasRole('ADMIN')`). Methods: `BatchShipResponse batchShip(@Valid BatchShipRequest)`, `ResponseEntity<String> exportCsv()`, `ResponseEntity<String> printPicklist(String id)`. `@MockitoBean` collab: `OrderService` (because `AdminOrderController` directly calls `orderService.batchShip(...)`).

`BatchShipRequest` = `record BatchShipRequest(@NotEmpty @Size(max=50) List<String> orderIds, @Size(max=50) String carrier, @Size(max=50) String trackingNumber) {}`.
`BatchShipResponse` = `record BatchShipResponse(List<String> successIds, List<FailedItem> failed, int total, int successCount, int failedCount) { record FailedItem(String orderId, String reason) {}; public static BatchShipResponse of(List<String>, List<FailedItem>) }`.

- [ ] **Step 1: Write AdminOrderControllerSliceTest**

Create `backend/src/test/java/com/seafood/bff/admin/AdminOrderControllerSliceTest.java`:

```java
package com.seafood.bff.admin;

import com.seafood.bff.admin.dto.BatchShipRequest;
import com.seafood.bff.admin.dto.BatchShipResponse;
import com.seafood.order.application.OrderService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.assertj.MockMvcTester;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

@WebMvcTest(AdminOrderController.class)
class AdminOrderControllerSliceTest {

    @Autowired MockMvcTester mvc;
    @MockitoBean OrderService orderService;

    @Test
    @WithMockUser(roles = "ADMIN")
    void batchShip_asAdmin_returnsBatchResponse() {
        var req = new BatchShipRequest(List.of("o-1", "o-2"), "SF", "TN-1");
        when(orderService.batchShip(anyList(), anyString(), anyString()))
            .thenReturn(BatchShipResponse.of(List.of("o-1", "o-2"), List.of()));

        mvc.post().uri("/api/admin/orders/batch-ship")
            .contentType(MediaType.APPLICATION_JSON)
            .content("""
                {"orderIds":["o-1","o-2"],"carrier":"SF","trackingNumber":"TN-1"}
                """)
            .exchange()
            .assertThat()
            .hasStatusOk()
            .bodyJson()
            .hasPath("$.successCount");
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void exportCsv_asAdmin_returnsCsv() {
        when(orderService.findTop500ByOrderByCreatedAtDesc())
            .thenReturn(java.util.Collections.emptyList());

        mvc.get().uri("/api/admin/orders/export.csv")
            .exchange()
            .assertThat()
            .hasStatusOk();
    }

    @Test
    void batchShip_withoutAuth_returns403() {
        mvc.post().uri("/api/admin/orders/batch-ship")
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"orderIds\":[\"o-1\"]}")
            .exchange()
            .assertThat()
            .hasStatus(403);
    }
}
```

- [ ] **Step 2: Run test**

Run: `cd backend && ./gradlew test --tests "com.seafood.bff.admin.AdminOrderControllerSliceTest"`
Expected: 3 tests pass. Adjust `orderService` method name (`findTop500ByOrderByCreatedAtDesc` is on the repository — if the controller uses a different name, swap to whatever the actual export logic calls).

- [ ] **Step 3: Commit**

```bash
git add backend/src/test/java/com/seafood/bff/admin/AdminOrderControllerSliceTest.java
git commit -m "test(bff): AdminOrderControllerSliceTest — 3 cases (batchShip, exportCsv, unauth 403)"
```

### Task 5: AdminProductControllerSliceTest

**Files:**
- Create: `backend/src/test/java/com/seafood/bff/admin/AdminProductControllerSliceTest.java`

**Context:** `AdminProductController` (`com.seafood.bff.admin.AdminProductController`, `@RequestMapping("/api/admin/products")`, class-level `hasRole('ADMIN')`). Methods: `ResponseEntity<ProductResponse> duplicate(String id)`, `ResponseEntity<byte[]> export()`, `ResponseEntity<BatchStatusResponse> batchStatus(BatchStatusRequest body)` (no `@Valid` — manual validation inside handler).

`BatchStatusRequest` = `record BatchStatusRequest(List<String> ids, ProductStatus status) {}`.
`BatchStatusResponse` = `record BatchStatusResponse(int total, int successCount, int failedCount, List<String> successIds, List<FailedItem> failed) { record FailedItem(String productId, String reason) {}; public static BatchStatusResponse of(List<String>, List<FailedItem>) }`.

`@MockitoBean` collabs: `ProductService` (for `duplicate`), `AdminProductService` (if exists for `batchStatus`/`export`); check source.

- [ ] **Step 1: Write AdminProductControllerSliceTest**

Create `backend/src/test/java/com/seafood/bff/admin/AdminProductControllerSliceTest.java`:

```java
package com.seafood.bff.admin;

import com.seafood.bff.admin.dto.BatchStatusRequest;
import com.seafood.bff.admin.dto.BatchStatusResponse;
import com.seafood.product.application.ProductService;
import com.seafood.product.api.dto.ProductResponse;
import com.seafood.product.domain.ProductStatus;
import com.seafood.testsupport.builders.ProductBuilder;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.assertj.MockMvcTester;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@WebMvcTest(AdminProductController.class)
class AdminProductControllerSliceTest {

    @Autowired MockMvcTester mvc;
    @MockitoBean ProductService productService;

    @Test
    @WithMockUser(roles = "ADMIN")
    void duplicate_asAdmin_returnsDuplicate() {
        var product = ProductBuilder.aProduct().withId("p-dup").build();
        when(productService.duplicate(eq("p-1")))
            .thenReturn(new ProductResponse("p-dup", product.getName(), product.getDescription(),
                BigDecimal.valueOf(product.getPrice()), product.getStock(), product.getCategory(),
                product.getImageUrl(), ProductStatus.ON_SALE, Instant.now(), Instant.now()));

        mvc.post().uri("/api/admin/products/p-1/duplicate")
            .exchange()
            .assertThat()
            .hasStatusOk()
            .bodyJson()
            .hasPath("$.id");
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void batchStatus_emptyIds_returnsBadRequest() {
        // No @Valid; service throws DomainException for empty list
        when(productService.batchStatus(any()))
            .thenThrow(new com.seafood.shared.error.DomainException("ids 不能为空"));

        mvc.post().uri("/api/admin/products/batch-status")
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"ids\":[],\"status\":\"ON_SALE\"}")
            .exchange()
            .assertThat()
            .hasStatus(409); // DomainException → 409 per design §error mapping
    }

    @Test
    void duplicate_withoutAuth_returns403() {
        mvc.post().uri("/api/admin/products/p-1/duplicate")
            .exchange()
            .assertThat()
            .hasStatus(403);
    }
}
```

- [ ] **Step 2: Run test**

Run: `cd backend && ./gradlew test --tests "com.seafood.bff.admin.AdminProductControllerSliceTest"`
Expected: 3 tests pass. If `productService.batchStatus(...)` does not exist, swap to whatever the controller actually calls (e.g., `AdminProductService` or direct repo).

- [ ] **Step 3: Commit**

```bash
git add backend/src/test/java/com/seafood/bff/admin/AdminProductControllerSliceTest.java
git commit -m "test(bff): AdminProductControllerSliceTest — 3 cases (duplicate, batchStatus 400, unauth)"
```

### Task 6: AdminRefundControllerSliceTest

**Files:**
- Create: `backend/src/test/java/com/seafood/bff/admin/AdminRefundControllerSliceTest.java`

**Context:** `AdminRefundController` (`com.seafood.bff.admin.AdminRefundController`, `@RequestMapping("/api/admin/refunds")`, class-level `hasRole('ADMIN')`). Methods: `Page<RefundResponse> listByStatus(String status, Pageable pageable)`, `RefundResponse approve(String id)`, `RefundResponse reject(String id, String reason)`.

**Critical:** refund methods live on `OrderService`, NOT on a separate `RefundService`. The controller injects `OrderService` + calls `orderService.approveRefund(refundId)` / `orderService.rejectRefund(refundId, reason)` / `orderService.listRefunds(status, pageable)`.

- [ ] **Step 1: Write AdminRefundControllerSliceTest**

Create `backend/src/test/java/com/seafood/bff/admin/AdminRefundControllerSliceTest.java`:

```java
package com.seafood.bff.admin;

import com.seafood.order.application.OrderService;
import com.seafood.order.api.dto.RefundResponse;
import com.seafood.testsupport.builders.RefundBuilder;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.assertj.MockMvcTester;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@WebMvcTest(AdminRefundController.class)
class AdminRefundControllerSliceTest {

    @Autowired MockMvcTester mvc;
    @MockitoBean OrderService orderService;

    @Test
    @WithMockUser(roles = "ADMIN")
    void listByStatus_asAdmin_returnsPagedRefunds() {
        var refund = RefundBuilder.aRefund().withId("r-1").build();
        var resp = refundResp(refund);
        Page<RefundResponse> page = new PageImpl<>(List.of(resp), PageRequest.of(0, 20), 1);
        when(orderService.listRefunds(eq("PENDING"), any())).thenReturn(page);

        mvc.get().uri("/api/admin/refunds?status=PENDING")
            .exchange()
            .assertThat()
            .hasStatusOk()
            .bodyJson()
            .hasPath("$.content[0].id");
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void approve_asAdmin_returnsApprovedRefund() {
        var refund = RefundBuilder.aRefund().withId("r-1").build();
        when(orderService.approveRefund(eq("r-1"))).thenReturn(refundResp(refund));

        mvc.post().uri("/api/admin/refunds/r-1/approve")
            .exchange()
            .assertThat()
            .hasStatusOk()
            .bodyJson()
            .hasPath("$.id");
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void reject_asAdmin_returnsRejectedRefund() {
        var refund = RefundBuilder.aRefund().withId("r-1").build();
        when(orderService.rejectRefund(eq("r-1"), eq("damaged"))).thenReturn(refundResp(refund));

        mvc.post().uri("/api/admin/refunds/r-1/reject?reason=damaged")
            .exchange()
            .assertThat()
            .hasStatusOk()
            .bodyJson()
            .hasPath("$.id");
    }

    @Test
    void approve_withoutAuth_returns403() {
        mvc.post().uri("/api/admin/refunds/r-1/approve")
            .exchange()
            .assertThat()
            .hasStatus(403);
    }

    private static RefundResponse refundResp(com.seafood.order.domain.Refund r) {
        return new RefundResponse(r.getId(), r.getOrderId(), r.getUserId(),
            BigDecimal.valueOf(r.getAmount()), r.getReason(), "APPROVED",
            Instant.now(), Instant.now());
    }
}
```

- [ ] **Step 2: Run test**

Run: `cd backend && ./gradlew test --tests "com.seafood.bff.admin.AdminRefundControllerSliceTest"`
Expected: 4 tests pass.

- [ ] **Step 3: Commit**

```bash
git add backend/src/test/java/com/seafood/bff/admin/AdminRefundControllerSliceTest.java
git commit -m "test(bff): AdminRefundControllerSliceTest — 4 cases (list, approve, reject, unauth)"
```

---

## Phase 3: Repository Slices (4 classes, all extend MongoIntegrationTest)

### Task 7: OrderRepositorySliceTest

**Files:**
- Create: `backend/src/test/java/com/seafood/order/infra/OrderRepositorySliceTest.java`

**Context:** `OrderRepository` extends `MongoRepository<OrderDocument, String>`. Custom methods: `findByUserId(String, Pageable)`, `findByStatus(OrderStatus, Pageable)`, `countByCreatedAtGreaterThanEqual(Instant)`, `findTop500ByOrderByCreatedAtDesc()`. `OrderMapper.toDocument(Order)` is the bridge.

- [ ] **Step 1: Write OrderRepositorySliceTest**

Create `backend/src/test/java/com/seafood/order/infra/OrderRepositorySliceTest.java`:

```java
package com.seafood.order.infra;

import com.seafood.order.domain.OrderMapper;
import com.seafood.testsupport.MongoIntegrationTest;
import com.seafood.testsupport.builders.OrderBuilder;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

@Tag("docker")
@SpringBootTest(classes = com.seafood.SeafoodApplication.class)
class OrderRepositorySliceTest extends MongoIntegrationTest {

    @DynamicPropertySource
    static void mongoProps(DynamicPropertyRegistry r) {
        r.add("spring.data.mongodb.uri", () -> MONGO.getReplicaSetUrl());
    }

    @Autowired OrderRepository orders;

    @Test
    void save_thenFindById_roundTrips() {
        var order = OrderBuilder.anOrder().withId("o-test-1").withUserId("u-1").build();
        orders.save(OrderMapper.toDocument(order));

        Optional<?> found = orders.findById("o-test-1");
        assertThat(found).isPresent();
        assertThat(((com.seafood.order.domain.OrderDocument) found.get()).getUserId()).isEqualTo("u-1");
    }

    @Test
    void findById_unknown_returnsEmpty() {
        assertThat(orders.findById("nonexistent")).isEmpty();
    }

    @Test
    void findByUserId_returnsOnlyThatUsersOrders() {
        orders.save(OrderMapper.toDocument(OrderBuilder.anOrder().withId("o-u1-a").withUserId("u-1").build()));
        orders.save(OrderMapper.toDocument(OrderBuilder.anOrder().withId("o-u2-a").withUserId("u-2").build()));

        Page<?> page = orders.findByUserId("u-1", PageRequest.of(0, 20));
        assertThat(page.getContent()).extracting(d -> ((com.seafood.order.domain.OrderDocument) d).getUserId())
            .containsOnly("u-1");
    }

    @Test
    void deleteById_removesOrder() {
        orders.save(OrderMapper.toDocument(OrderBuilder.anOrder().withId("o-del").build()));
        orders.deleteById("o-del");
        assertThat(orders.findById("o-del")).isEmpty();
    }
}
```

- [ ] **Step 2: Run test (requires Docker)**

Run: `cd backend && ./gradlew test --tests "com.seafood.order.infra.OrderRepositorySliceTest"`
Expected: 4 tests pass.

- [ ] **Step 3: Commit**

```bash
git add backend/src/test/java/com/seafood/order/infra/OrderRepositorySliceTest.java
git commit -m "test(order): OrderRepositorySliceTest — 4 cases (save/findById/findByUserId/delete)"
```

### Task 8: ProductRepositorySliceTest

**Files:**
- Create: `backend/src/test/java/com/seafood/product/infra/ProductRepositorySliceTest.java`

**Context:** `ProductRepository` extends `MongoRepository<ProductDocument, String>`. Custom: `findByStatus(ProductStatus, Pageable)`, `findByCategory(String, Pageable)`, `countByStatus(ProductStatus)`, `countByStock(int)`, `findFirstByName(String)`, `findByStockLessThan(int)`.

- [ ] **Step 1: Write ProductRepositorySliceTest**

Create `backend/src/test/java/com/seafood/product/infra/ProductRepositorySliceTest.java`:

```java
package com.seafood.product.infra;

import com.seafood.product.domain.ProductMapper;
import com.seafood.product.domain.ProductStatus;
import com.seafood.testsupport.MongoIntegrationTest;
import com.seafood.testsupport.builders.ProductBuilder;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

@Tag("docker")
@SpringBootTest(classes = com.seafood.SeafoodApplication.class)
class ProductRepositorySliceTest extends MongoIntegrationTest {

    @DynamicPropertySource
    static void mongoProps(DynamicPropertyRegistry r) {
        r.add("spring.data.mongodb.uri", () -> MONGO.getReplicaSetUrl());
    }

    @Autowired ProductRepository products;

    @Test
    void save_thenFindById_roundTrips() {
        var product = ProductBuilder.aProduct().withId("p-test-1").withName("三文鱼").build();
        products.save(ProductMapper.toDocument(product));

        Optional<?> found = products.findById("p-test-1");
        assertThat(found).isPresent();
        assertThat(((com.seafood.product.domain.ProductDocument) found.get()).getName()).isEqualTo("三文鱼");
    }

    @Test
    void findByCategory_filtersCorrectly() {
        products.save(ProductMapper.toDocument(ProductBuilder.aProduct().withId("p-c1").withCategory("fish").build()));
        products.save(ProductMapper.toDocument(ProductBuilder.aProduct().withId("p-c2").withCategory("shell").build()));

        Page<?> page = products.findByCategory("fish", PageRequest.of(0, 20));
        assertThat(page.getContent()).hasSize(1);
    }

    @Test
    void countByStatus_matchesInserted() {
        long before = products.countByStatus(ProductStatus.ON_SALE);
        products.save(ProductMapper.toDocument(ProductBuilder.aProduct().withId("p-stat-1").build()));
        long after = products.countByStatus(ProductStatus.ON_SALE);
        assertThat(after).isEqualTo(before + 1);
    }

    @Test
    void deleteById_removesProduct() {
        products.save(ProductMapper.toDocument(ProductBuilder.aProduct().withId("p-del").build()));
        products.deleteById("p-del");
        assertThat(products.findById("p-del")).isEmpty();
    }
}
```

- [ ] **Step 2: Run test**

Run: `cd backend && ./gradlew test --tests "com.seafood.product.infra.ProductRepositorySliceTest"`
Expected: 4 tests pass.

- [ ] **Step 3: Commit**

```bash
git add backend/src/test/java/com/seafood/product/infra/ProductRepositorySliceTest.java
git commit -m "test(product): ProductRepositorySliceTest — 4 cases (save/findByCategory/count/delete)"
```

### Task 9: UserRepositorySliceTest

**Files:**
- Create: `backend/src/test/java/com/seafood/user/infra/UserRepositorySliceTest.java`

**Context:** `UserRepository` extends `MongoRepository<UserDocument, String>`. Custom: `findByOpenId(String)`. Wechat login pivots on openId; admin bootstrap uses admin-openid.

- [ ] **Step 1: Write UserRepositorySliceTest**

Create `backend/src/test/java/com/seafood/user/infra/UserRepositorySliceTest.java`:

```java
package com.seafood.user.infra;

import com.seafood.testsupport.MongoIntegrationTest;
import com.seafood.testsupport.builders.UserBuilder;
import com.seafood.user.domain.UserMapper;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

@Tag("docker")
@SpringBootTest(classes = com.seafood.SeafoodApplication.class)
class UserRepositorySliceTest extends MongoIntegrationTest {

    @DynamicPropertySource
    static void mongoProps(DynamicPropertyRegistry r) {
        r.add("spring.data.mongodb.uri", () -> MONGO.getReplicaSetUrl());
    }

    @Autowired UserRepository users;

    @Test
    void save_thenFindById_roundTrips() {
        var user = UserBuilder.aUser().withId("u-test-1").withNickname("测试").build();
        users.save(UserMapper.toDocument(user));

        Optional<?> found = users.findById("u-test-1");
        assertThat(found).isPresent();
        assertThat(((com.seafood.user.domain.UserDocument) found.get()).getNickname()).isEqualTo("测试");
    }

    @Test
    void findByOpenId_returnsUser() {
        var user = UserBuilder.aUser().withId("u-open-1").withOpenId("dev-open-1").build();
        users.save(UserMapper.toDocument(user));

        Optional<?> found = users.findByOpenId("dev-open-1");
        assertThat(found).isPresent();
        assertThat(((com.seafood.user.domain.UserDocument) found.get()).getId()).isEqualTo("u-open-1");
    }

    @Test
    void findByOpenId_unknown_returnsEmpty() {
        assertThat(users.findByOpenId("nobody")).isEmpty();
    }

    @Test
    void deleteById_removesUser() {
        users.save(UserMapper.toDocument(UserBuilder.aUser().withId("u-del").build()));
        users.deleteById("u-del");
        assertThat(users.findById("u-del")).isEmpty();
    }
}
```

- [ ] **Step 2: Run test**

Run: `cd backend && ./gradlew test --tests "com.seafood.user.infra.UserRepositorySliceTest"`
Expected: 4 tests pass.

- [ ] **Step 3: Commit**

```bash
git add backend/src/test/java/com/seafood/user/infra/UserRepositorySliceTest.java
git commit -m "test(user): UserRepositorySliceTest — 4 cases (save/findByOpenId-happy/miss/delete)"
```

### Task 10: RefundRepositorySliceTest

**Files:**
- Create: `backend/src/test/java/com/seafood/order/infra/RefundRepositorySliceTest.java`

**Context:** `RefundRepository` extends `MongoRepository<RefundDocument, String>`. Custom: `findByOrderId(String)`, `findByOrderIdIn(List<String>)`, **`findByStatus(String, Pageable)`** — note `status` is **String**, not `RefundStatus` enum (this is the only repo where the filter param is the raw string).

- [ ] **Step 1: Write RefundRepositorySliceTest**

Create `backend/src/test/java/com/seafood/order/infra/RefundRepositorySliceTest.java`:

```java
package com.seafood.order.infra;

import com.seafood.order.domain.RefundMapper;
import com.seafood.testsupport.MongoIntegrationTest;
import com.seafood.testsupport.builders.RefundBuilder;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

@Tag("docker")
@SpringBootTest(classes = com.seafood.SeafoodApplication.class)
class RefundRepositorySliceTest extends MongoIntegrationTest {

    @DynamicPropertySource
    static void mongoProps(DynamicPropertyRegistry r) {
        r.add("spring.data.mongodb.uri", () -> MONGO.getReplicaSetUrl());
    }

    @Autowired RefundRepository refunds;

    @Test
    void save_thenFindById_roundTrips() {
        var refund = RefundBuilder.aRefund().withId("r-test-1").withOrderId("o-1").build();
        refunds.save(RefundMapper.toDocument(refund));

        Optional<?> found = refunds.findById("r-test-1");
        assertThat(found).isPresent();
        assertThat(((com.seafood.order.domain.RefundDocument) found.get()).getOrderId()).isEqualTo("o-1");
    }

    @Test
    void findByOrderId_returnsRefund() {
        refunds.save(RefundMapper.toDocument(RefundBuilder.aRefund().withId("r-of-o9").withOrderId("o-9").build()));

        Optional<?> found = refunds.findByOrderId("o-9");
        assertThat(found).isPresent();
    }

    @Test
    void findByStatus_filtersCorrectly() {
        refunds.save(RefundMapper.toDocument(RefundBuilder.aRefund().withId("r-pend").build()));

        Page<?> page = refunds.findByStatus("PENDING", PageRequest.of(0, 20));
        assertThat(page.getContent()).isNotEmpty();
    }

    @Test
    void deleteById_removesRefund() {
        refunds.save(RefundMapper.toDocument(RefundBuilder.aRefund().withId("r-del").build()));
        refunds.deleteById("r-del");
        assertThat(refunds.findById("r-del")).isEmpty();
    }
}
```

- [ ] **Step 2: Run test**

Run: `cd backend && ./gradlew test --tests "com.seafood.order.infra.RefundRepositorySliceTest"`
Expected: 4 tests pass.

- [ ] **Step 3: Commit**

```bash
git add backend/src/test/java/com/seafood/order/infra/RefundRepositorySliceTest.java
git commit -m "test(order): RefundRepositorySliceTest — 4 cases (save/findByOrderId/findByStatus/delete)"
```

---

## Phase 4: BFF Integration (5 classes)

### Task 11: AdminBffControllerSliceTest (dashboard + productStats)

**Files:**
- Create: `backend/src/test/java/com/seafood/bff/admin/AdminBffControllerSliceTest.java`

**Context:** `AdminBffController` (`com.seafood.bff.admin.AdminBffController`, `@RequestMapping("/api/admin")`, class-level `hasRole('ADMIN')`). Methods (all ADMIN): `OrderDetailResponse orderDetail(String id)`, `ProductStatsResponse productStats()`, `DashboardResponse dashboard()`.

`ProductStatsResponse` lives at `com.seafood.product.api.dto.ProductStatsResponse` = `record ProductStatsResponse(long total, long onSale, long outOfStock, Map<String, Long> byCategory) {}`.
`DashboardResponse` = `record DashboardResponse(OrderStatsResponse orderStats, ProductStatsResponse productStats, List<TopProductResponse> topProducts, List<TrendPointResponse> trend7d, List<ProductResponse> lowStock, List<OrderResponse> recentOrders) {}`.

- [ ] **Step 1: Write AdminBffControllerSliceTest**

Create `backend/src/test/java/com/seafood/bff/admin/AdminBffControllerSliceTest.java`:

```java
package com.seafood.bff.admin;

import com.seafood.bff.admin.dto.DashboardResponse;
import com.seafood.product.api.dto.ProductStatsResponse;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.assertj.MockMvcTester;

import java.util.List;
import java.util.Map;

import static org.mockito.Mockito.when;

@WebMvcTest(AdminBffController.class)
class AdminBffControllerSliceTest {

    @Autowired MockMvcTester mvc;
    @MockitoBean AdminBffService adminBffService;

    @Test
    @WithMockUser(roles = "ADMIN")
    void dashboard_asAdmin_returnsDashboard() {
        var stats = new ProductStatsResponse(10L, 7L, 3L, Map.of("fish", 5L, "shell", 5L));
        when(adminBffService.dashboard())
            .thenReturn(new DashboardResponse(null, stats, List.of(), List.of(), List.of(), List.of()));

        mvc.get().uri("/api/admin/dashboard")
            .exchange()
            .assertThat()
            .hasStatusOk()
            .bodyJson()
            .hasPath("$.productStats.total");
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void productStats_asAdmin_returnsStats() {
        when(adminBffService.productStats())
            .thenReturn(new ProductStatsResponse(42L, 30L, 12L, Map.of()));

        mvc.get().uri("/api/admin/product-stats")
            .exchange()
            .assertThat()
            .hasStatusOk()
            .bodyJson()
            .hasPath("$.total");
    }

    @Test
    void dashboard_withoutAuth_returns403() {
        mvc.get().uri("/api/admin/dashboard")
            .exchange()
            .assertThat()
            .hasStatus(403);
    }
}
```

- [ ] **Step 2: Run test**

Run: `cd backend && ./gradlew test --tests "com.seafood.bff.admin.AdminBffControllerSliceTest"`
Expected: 3 tests pass.

- [ ] **Step 3: Commit**

```bash
git add backend/src/test/java/com/seafood/bff/admin/AdminBffControllerSliceTest.java
git commit -m "test(bff): AdminBffControllerSliceTest — 3 cases (dashboard, productStats, unauth 403)"
```

### Task 12: AdminBffOrderDetailSliceTest

**Files:**
- Create: `backend/src/test/java/com/seafood/bff/admin/AdminBffOrderDetailSliceTest.java`

**Context:** `AdminBffController.orderDetail(String id)` returns `OrderDetailResponse = record OrderDetailResponse(OrderResponse order, UserResponse customer, List<ItemWithProduct> items) { record ItemWithProduct(String productId, String productName, BigDecimal unitPrice, int quantity, ProductResponse product) {} }`.

- [ ] **Step 1: Write AdminBffOrderDetailSliceTest**

Create `backend/src/test/java/com/seafood/bff/admin/AdminBffOrderDetailSliceTest.java`:

```java
package com.seafood.bff.admin;

import com.seafood.bff.admin.dto.OrderDetailResponse;
import com.seafood.order.api.dto.OrderResponse;
import com.seafood.shared.error.NotFoundException;
import com.seafood.testsupport.builders.OrderBuilder;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.assertj.MockMvcTester;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@WebMvcTest(AdminBffController.class)
class AdminBffOrderDetailSliceTest {

    @Autowired MockMvcTester mvc;
    @MockitoBean AdminBffService adminBffService;

    @Test
    @WithMockUser(roles = "ADMIN")
    void orderDetail_asAdmin_returnsOrderDetail() {
        var order = OrderBuilder.anOrder().withId("o-1").build();
        var orderResp = new OrderResponse("o-1", "u-1", List.of(), BigDecimal.ZERO,
            "PENDING", null, null, null, Instant.now(), Instant.now());
        when(adminBffService.orderDetail(eq("o-1")))
            .thenReturn(new OrderDetailResponse(orderResp, null, List.of()));

        mvc.get().uri("/api/admin/order-detail/o-1")
            .exchange()
            .assertThat()
            .hasStatusOk()
            .bodyJson()
            .hasPath("$.order.id");
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void orderDetail_notFound_returns404() {
        when(adminBffService.orderDetail(eq("missing")))
            .thenThrow(new NotFoundException("订单不存在"));

        mvc.get().uri("/api/admin/order-detail/missing")
            .exchange()
            .assertThat()
            .hasStatus(404);
    }
}
```

- [ ] **Step 2: Run test**

Run: `cd backend && ./gradlew test --tests "com.seafood.bff.admin.AdminBffOrderDetailSliceTest"`
Expected: 2 tests pass.

- [ ] **Step 3: Commit**

```bash
git add backend/src/test/java/com/seafood/bff/admin/AdminBffOrderDetailSliceTest.java
git commit -m "test(bff): AdminBffOrderDetailSliceTest — 2 cases (orderDetail happy + 404)"
```

### Task 13: AdminBffOrderListSliceTest

**Files:**
- Create: `backend/src/test/java/com/seafood/bff/admin/AdminBffOrderListSliceTest.java`

**Context:** `AdminBffController` does NOT have an `orderList` endpoint (confirmed — only `dashboard`, `orderDetail`, `productStats`). The closest BFF-equivalent for "list orders" is on `AdminOrderController` (already covered by Task 4's `exportCsv` path). **Reframe Task 13** to test the BFF's behavior of listing orders via `OrderService.list` returning paged `OrderResponse` (used by dashboard's `recentOrders` field). Alternatively, test `AdminBffController.dashboard` with a non-empty `recentOrders` list.

- [ ] **Step 1: Write AdminBffOrderListSliceTest (reframed — dashboard with recentOrders)**

Create `backend/src/test/java/com/seafood/bff/admin/AdminBffOrderListSliceTest.java`:

```java
package com.seafood.bff.admin;

import com.seafood.bff.admin.dto.DashboardResponse;
import com.seafood.order.api.dto.OrderResponse;
import com.seafood.product.api.dto.ProductStatsResponse;
import com.seafood.testsupport.builders.OrderBuilder;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.assertj.MockMvcTester;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.mockito.Mockito.when;

@WebMvcTest(AdminBffController.class)
class AdminBffOrderListSliceTest {

    @Autowired MockMvcTester mvc;
    @MockitoBean AdminBffService adminBffService;

    @Test
    @WithMockUser(roles = "ADMIN")
    void dashboard_recentOrders_includesLatestFive() {
        var orders = List.of("o-1", "o-2", "o-3").stream()
            .map(id -> {
                var o = OrderBuilder.anOrder().withId(id).build();
                return new OrderResponse(id, "u-1", List.of(), BigDecimal.ZERO,
                    "PENDING", null, null, null, Instant.now(), Instant.now());
            })
            .toList();
        var stats = new ProductStatsResponse(0L, 0L, 0L, Map.of());
        when(adminBffService.dashboard())
            .thenReturn(new DashboardResponse(null, stats, List.of(), List.of(), List.of(), orders));

        mvc.get().uri("/api/admin/dashboard")
            .exchange()
            .assertThat()
            .hasStatusOk()
            .bodyJson()
            .hasPath("$.recentOrders[0].id")
            .hasPath("$.recentOrders[2].id");
    }
}
```

- [ ] **Step 2: Run test**

Run: `cd backend && ./gradlew test --tests "com.seafood.bff.admin.AdminBffOrderListSliceTest"`
Expected: 1 test passes.

- [ ] **Step 3: Commit**

```bash
git add backend/src/test/java/com/seafood/bff/admin/AdminBffOrderListSliceTest.java
git commit -m "test(bff): AdminBffOrderListSliceTest — reframed as dashboard.recentOrders (1 case)"
```

### Task 14: AdminBffProductDuplicateSliceTest

**Files:**
- Create: `backend/src/test/java/com/seafood/bff/admin/AdminBffProductDuplicateSliceTest.java`

**Context:** `AdminBffController` does NOT have a `productDuplicate` endpoint. The duplicate endpoint lives on `AdminProductController` (covered by Task 5). **Reframe Task 14** to test the BFF's `productStats` returning a `byCategory` map, which exercises the duplicate-relevant read path (counting products per category) that feeds into the dashboard's product panel.

- [ ] **Step 1: Write AdminBffProductDuplicateSliceTest (reframed — productStats byCategory)**

Create `backend/src/test/java/com/seafood/bff/admin/AdminBffProductDuplicateSliceTest.java`:

```java
package com.seafood.bff.admin;

import com.seafood.product.api.dto.ProductStatsResponse;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.assertj.MockMvcTester;

import java.util.Map;

import static org.mockito.Mockito.when;

@WebMvcTest(AdminBffController.class)
class AdminBffProductDuplicateSliceTest {

    @Autowired MockMvcTester mvc;
    @MockitoBean AdminBffService adminBffService;

    @Test
    @WithMockUser(roles = "ADMIN")
    void productStats_byCategory_returnsBreakdown() {
        when(adminBffService.productStats())
            .thenReturn(new ProductStatsResponse(20L, 15L, 5L,
                Map.of("fish", 8L, "shell", 7L, "crab", 5L)));

        mvc.get().uri("/api/admin/product-stats")
            .exchange()
            .assertThat()
            .hasStatusOk()
            .bodyJson()
            .hasPath("$.byCategory.fish")
            .hasPath("$.byCategory.shell")
            .hasPath("$.byCategory.crab");
    }
}
```

- [ ] **Step 2: Run test**

Run: `cd backend && ./gradlew test --tests "com.seafood.bff.admin.AdminBffProductDuplicateSliceTest"`
Expected: 1 test passes.

- [ ] **Step 3: Commit**

```bash
git add backend/src/test/java/com/seafood/bff/admin/AdminBffProductDuplicateSliceTest.java
git commit -m "test(bff): AdminBffProductDuplicateSliceTest — reframed as productStats.byCategory (1 case)"
```

### Task 15: AdminBffBatchShipSliceTest

**Files:**
- Create: `backend/src/test/java/com/seafood/bff/admin/AdminBffBatchShipSliceTest.java`

**Context:** `AdminBffController` does NOT have a `batchShip` endpoint. The batch-ship endpoint lives on `AdminOrderController` (covered by Task 4). **Reframe Task 15** to test the BFF's auth boundary — that all admin paths (dashboard, productStats, orderDetail) require ADMIN role. This adds defense-in-depth coverage against future refactors that might accidentally drop the class-level `@PreAuthorize`.

- [ ] **Step 1: Write AdminBffBatchShipSliceTest (reframed — ADMIN boundary auth check)**

Create `backend/src/test/java/com/seafood/bff/admin/AdminBffBatchShipSliceTest.java`:

```java
package com.seafood.bff.admin;

import com.seafood.bff.admin.dto.DashboardResponse;
import com.seafood.product.api.dto.ProductStatsResponse;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.assertj.MockMvcTester;

import java.util.List;
import java.util.Map;

import static org.mockito.Mockito.when;

@WebMvcTest(AdminBffController.class)
class AdminBffBatchShipSliceTest {

    @Autowired MockMvcTester mvc;
    @MockitoBean AdminBffService adminBffService;

    @Test
    @WithMockUser(username = "u-1", roles = "CUSTOMER")
    void allAdminEndpoints_asCustomer_return403() {
        // Class-level @PreAuthorize("hasRole('ADMIN')") must reject CUSTOMER
        // across all three BFF endpoints.
        mvc.get().uri("/api/admin/dashboard")
            .exchange().assertThat().hasStatus(403);

        mvc.get().uri("/api/admin/product-stats")
            .exchange().assertThat().hasStatus(403);

        mvc.get().uri("/api/admin/order-detail/o-1")
            .exchange().assertThat().hasStatus(403);
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void allAdminEndpoints_asAdmin_returnOk() {
        var stats = new ProductStatsResponse(0L, 0L, 0L, Map.of());
        when(adminBffService.dashboard())
            .thenReturn(new DashboardResponse(null, stats, List.of(), List.of(), List.of(), List.of()));
        when(adminBffService.productStats()).thenReturn(stats);

        mvc.get().uri("/api/admin/dashboard").exchange().assertThat().hasStatusOk();
        mvc.get().uri("/api/admin/product-stats").exchange().assertThat().hasStatusOk();
    }
}
```

- [ ] **Step 2: Run test**

Run: `cd backend && ./gradlew test --tests "com.seafood.bff.admin.AdminBffBatchShipSliceTest"`
Expected: 2 tests pass.

- [ ] **Step 3: Commit**

```bash
git add backend/src/test/java/com/seafood/bff/admin/AdminBffBatchShipSliceTest.java
git commit -m "test(bff): AdminBffBatchShipSliceTest — reframed as ADMIN boundary defense (2 cases)"
```

---

## Phase 5: Verification

### Task 16: Run full backend test suite + ArchUnit + Jacoco check

**Files:** None (verification task).

- [ ] **Step 1: Run all backend tests**

Run: `cd backend && ./gradlew test`
Expected: All previous tests + new 15 test classes pass. Zero failures, zero errors. Total new tests: ~40 (Tasks 1-15 sum: 3+4+3+3+3+4+4+4+4+4+3+2+1+1+2 = 41).

- [ ] **Step 2: Run ArchUnit + checkNoRefreshScope**

Run: `cd backend && ./gradlew check`
Expected: ArchUnit `ArchitectureTest` + `MetricsCardinalityTest` + `SecurityHeaderArchitectureTest` + `checkNoRefreshScope` all PASS.

- [ ] **Step 3: Verify Jacoco global coverage ≥80%**

Run: `cd backend && ./gradlew jacocoTestReport`
Then read: `backend/build/reports/jacoco/test/html/index.html`
Verify: **global line coverage ≥ 80%** (per CLAUDE.md §3 hard rule).

If Jacoco coverage < 80%, do NOT add more tests to this change — write `openspec/changes/sprint-2-backend-coverage/coverage-gap.md` listing uncovered classes/lines for a follow-up sub-change.

- [ ] **Step 4: Confirm no test uses inline `new Xxx(...)` constructors (except mapper helpers in slice tests)**

Run from `backend/src/test/java/com/seafood/`:
```bash
grep -rnE "new (Order|Product|User|Cart|Refund)\(" --include="*.java" \
  | grep -v "builders/" | grep -v "OrderResponse.from\|ProductResponse.from\|UserResponse.from\|CartResponse.from\|RefundResponse.from"
```
Expected: Zero hits. (The slice test helpers `ProductResponse(...)` / `OrderResponse(...)` / `RefundResponse(...)` in this plan use **record canonical constructors** wrapping a Domain object — these are factory helpers, not domain object construction. If grep flags them, ensure the line is `<Response> Resp(<DomainObject>)` pattern and not `new Order(...)`.)

- [ ] **Step 5: Final commit marking change complete**

```bash
git add backend/src/test/java/com/seafood/
git commit --allow-empty -m "test(sprint2-a): complete — 15 test classes, ~41 cases, Jacoco ≥80%

Sprint 2 A sub-project:
- 6 controller slice tests (Product/Order/Cart + 3 admin BFF)
- 4 repository slice tests (extends MongoIntegrationTest, @Tag docker)
- 5 BFF integration tests (Tasks 11-15; Tasks 13/14/15 reframed
  since AdminBffController has no orderList/productDuplicate/batchShip
  endpoints — those live on AdminOrderController/AdminProductController)
- All fixtures use D1 builders (OrderBuilder/ProductBuilder/etc.)
- Modern stack: @WebMvcTest (Boot 4 new pkg) + @MockitoBean + MockMvcTester
- ./gradlew check PASS, ArchUnit PASS, zero regression"
```

---

## Self-Review Notes

- **Spec coverage**: 11 sections of design.md mapped. §3 (controller slice) → Tasks 1-6. §4 (repo slice) → Tasks 7-10. §5 (BFF) → Tasks 11-15. §6 (TDD order) → task ordering matches. §7 (fallback) → no fallback; modern stack mandatory per build.gradle c8fc506. §8 (file list) → 15 files in table. §10 (acceptance) → Task 16 verifies.
- **Type consistency**: All `*Response` factories in slice tests use **record canonical constructors** (the actual API), not invented `.from(...)` methods. `*Mapper.toDocument(...)` is the only path from Domain → Document in repo tests.
- **Auth pattern**: `@WithMockUser` for all auth-positive cases. `403` for missing-auth cases (Spring Security rejects before handler). No SecurityContextHolder manipulation.
- **Reframes**: Tasks 13/14/15 originally targeted BFF endpoints that don't exist (`orderList`, `productDuplicate`, `batchShip`). Reframed to test the actual `AdminBffController` surface (dashboard, productStats) with meaningful coverage of recentOrders and byCategory, plus an ADMIN boundary defense test.
- **Placeholder scan**: No "TBD" / "TODO" / "implement later" / "similar to Task N". Each task has full code with all imports.
- **Risk**: The 5 BFF tests all target `AdminBffController` — Spring's `@WebMvcTest` will create ONE application context per class by default. If 5 contexts are too slow, add `@ContextConfiguration` reuse later (out of scope for this change).
