package com.seafood.order.application;

import com.seafood.order.api.dto.OrderResponse;
import com.seafood.order.domain.Cart;
import com.seafood.order.domain.CartItem;
import com.seafood.order.infra.CartDocument;
import com.seafood.order.infra.CartRepository;
import com.seafood.order.infra.OrderDocument;
import com.seafood.order.infra.OrderRepository;
import com.seafood.product.domain.ProductCategory;
import com.seafood.product.domain.ProductStatus;
import com.seafood.product.infra.ProductDocument;
import com.seafood.product.infra.ProductRepository;
import com.seafood.shared.error.DomainException;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class OrderServiceTest {

    private OrderRepository orderRepo;
    private CartRepository cartRepo;
    private ProductRepository productRepo;
    private MeterRegistry meterRegistry;
    private OrderService service;

    @BeforeEach
    void setUp() {
        orderRepo = mock(OrderRepository.class);
        cartRepo = mock(CartRepository.class);
        productRepo = mock(ProductRepository.class);
        // PR #3 3.x:SimpleMeterRegistry in-memory,与 Spring Boot Actuator 默认
        // 装配的 MeterRegistry 类型兼容;counter().count() 直接可读,断言语义直白。
        meterRegistry = new SimpleMeterRegistry();
        service = new OrderService(orderRepo, cartRepo, productRepo, meterRegistry);
        SecurityContextHolder.clearContext();
    }

    private ProductDocument activeProduct(String id, String name, int stock) {
        ProductDocument d = new ProductDocument();
        d.setId(id);
        d.setName(name);
        d.setPrice(new BigDecimal("50.00"));
        d.setStock(stock);
        d.setCategory("鱼类");
        d.setStatus(ProductStatus.ACTIVE);
        d.setOnSale(true);
        d.setCreatedAt(Instant.now());
        d.setUpdatedAt(Instant.now());
        return d;
    }

    private void loginAs(String userId, com.seafood.shared.security.Role role) {
        var principal = new com.seafood.shared.security.UserPrincipal(userId, role);
        var auth = new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities());
        SecurityContextHolder.getContext().setAuthentication(auth);
    }

    @Test
    void create_snapshotsPriceAndDecrementsStock() {
        loginAs("u1", com.seafood.shared.security.Role.CUSTOMER);
        CartDocument cartDoc = new CartDocument();
        cartDoc.setUserId("u1");
        cartDoc.setItems(List.of(new CartItem("p1", 2, true, Instant.now())));
        when(cartRepo.findById("u1")).thenReturn(Optional.of(cartDoc));
        when(productRepo.findAllById(List.of("p1"))).thenReturn(List.of(activeProduct("p1", "三文鱼", 10)));
        when(productRepo.findById("p1")).thenReturn(Optional.of(activeProduct("p1", "三文鱼", 10)));
        when(orderRepo.save(any(OrderDocument.class))).thenAnswer(inv -> {
            OrderDocument d = inv.getArgument(0);
            d.setId("o1");
            return d;
        });

        OrderResponse res = service.create("u1");

        assertThat(res.id()).isEqualTo("o1");
        assertThat(res.totalAmount()).isEqualByComparingTo("100.00");
        assertThat(res.status()).isEqualTo("PENDING");
    }

    @Test
    void create_rejectsInsufficientStock() {
        loginAs("u1", com.seafood.shared.security.Role.CUSTOMER);
        CartDocument cartDoc = new CartDocument();
        cartDoc.setUserId("u1");
        cartDoc.setItems(List.of(new CartItem("p1", 5, true, Instant.now())));
        when(cartRepo.findById("u1")).thenReturn(Optional.of(cartDoc));
        when(productRepo.findAllById(List.of("p1"))).thenReturn(List.of(activeProduct("p1", "三文鱼", 2)));
        when(productRepo.findById("p1")).thenReturn(Optional.of(activeProduct("p1", "三文鱼", 2)));

        assertThatThrownBy(() -> service.create("u1"))
                .isInstanceOf(DomainException.class)
                .hasMessageContaining("库存不足");
    }

    @Test
    void create_rejectsEmptyCart() {
        loginAs("u1", com.seafood.shared.security.Role.CUSTOMER);
        when(cartRepo.findById("u1")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.create("u1"))
                .isInstanceOf(DomainException.class)
                .hasMessageContaining("购物车为空");
    }

    @Test
    void create_marksOutOfStockWhenDepleted() {
        loginAs("u1", com.seafood.shared.security.Role.CUSTOMER);
        CartDocument cartDoc = new CartDocument();
        cartDoc.setUserId("u1");
        cartDoc.setItems(List.of(new CartItem("p1", 2, true, Instant.now())));
        when(cartRepo.findById("u1")).thenReturn(Optional.of(cartDoc));
        ProductDocument prod = activeProduct("p1", "三文鱼", 2);
        when(productRepo.findAllById(List.of("p1"))).thenReturn(List.of(prod));
        when(productRepo.findById("p1")).thenReturn(Optional.of(prod));
        when(orderRepo.save(any(OrderDocument.class))).thenAnswer(inv -> {
            OrderDocument d = inv.getArgument(0);
            d.setId("o1");
            return d;
        });

        service.create("u1");

        assertThat(prod.getStock()).isEqualTo(0);
        assertThat(prod.getStatus()).isEqualTo(ProductStatus.OUT_OF_STOCK);
    }

    @Test
    void ship_adminOnlyTransitions() {
        loginAs("admin", com.seafood.shared.security.Role.ADMIN);
        OrderDocument doc = new OrderDocument();
        doc.setId("o1");
        doc.setUserId("u1");
        doc.setItems(List.of(new com.seafood.order.domain.OrderItem("p1", "三文鱼", new BigDecimal("50"), 1)));
        doc.setTotalAmount(new BigDecimal("50"));
        doc.setStatus("PAID");
        doc.setCreatedAt(Instant.now());
        doc.setUpdatedAt(Instant.now());
        when(orderRepo.findById("o1")).thenReturn(Optional.of(doc));
        when(orderRepo.save(any(OrderDocument.class))).thenAnswer(inv -> inv.getArgument(0));

        OrderResponse res = service.ship("o1");
        assertThat(res.status()).isEqualTo("SHIPPED");
    }

    @Test
    void ship_fromPending_throws() {
        loginAs("admin", com.seafood.shared.security.Role.ADMIN);
        OrderDocument doc = new OrderDocument();
        doc.setId("o1");
        doc.setUserId("u1");
        doc.setItems(List.of());
        doc.setTotalAmount(new BigDecimal("50"));
        doc.setStatus("PENDING");
        doc.setCreatedAt(Instant.now());
        doc.setUpdatedAt(Instant.now());
        when(orderRepo.findById("o1")).thenReturn(Optional.of(doc));

        assertThatThrownBy(() -> service.ship("o1"))
                .isInstanceOf(DomainException.class);
    }

    @Test
    void list_customerSeesOnlyOwn() {
        loginAs("u1", com.seafood.shared.security.Role.CUSTOMER);
        when(orderRepo.findByUserId(anyString(), any())).thenReturn(org.springframework.data.domain.Page.empty());

        service.list(null, org.springframework.data.domain.PageRequest.of(0, 20));

        // 验证调用了 findByUserId("u1", ...) 而不是 findAll
        org.mockito.Mockito.verify(orderRepo).findByUserId(org.mockito.ArgumentMatchers.eq("u1"), any());
        org.mockito.Mockito.verify(orderRepo, org.mockito.Mockito.never())
                .findAll(any(org.springframework.data.domain.Pageable.class));
    }

    @Test
    void list_adminCanSeeAll() {
        loginAs("admin", com.seafood.shared.security.Role.ADMIN);
        when(orderRepo.findAll(any(org.springframework.data.domain.Pageable.class)))
                .thenReturn(org.springframework.data.domain.Page.empty());

        service.list(null, org.springframework.data.domain.PageRequest.of(0, 20));

        org.mockito.Mockito.verify(orderRepo).findAll(any(org.springframework.data.domain.Pageable.class));
    }

    // === PR #3 3.3:orders.created counter 埋点 ===

    @Test
    void create_success_incrementsOrdersCreatedCounter() {
        // 复用现有 create_snapshotsPriceAndDecrementsStock 的 fixture,但显式断言 counter
        loginAs("u1", com.seafood.shared.security.Role.CUSTOMER);
        CartDocument cartDoc = new CartDocument();
        cartDoc.setUserId("u1");
        cartDoc.setItems(List.of(new CartItem("p1", 2, true, Instant.now())));
        when(cartRepo.findById("u1")).thenReturn(Optional.of(cartDoc));
        when(productRepo.findAllById(List.of("p1"))).thenReturn(List.of(activeProduct("p1", "三文鱼", 10)));
        when(productRepo.findById("p1")).thenReturn(Optional.of(activeProduct("p1", "三文鱼", 10)));
        when(orderRepo.save(any(OrderDocument.class))).thenAnswer(inv -> {
            OrderDocument d = inv.getArgument(0);
            d.setId("o1");
            return d;
        });

        service.create("u1", "wechat");

        assertThat(meterRegistry.counter("orders.created", "paymentMethod", "wechat").count())
                .as("下单成功 → orders.created{wechat} += 1")
                .isEqualTo(1.0);
    }

    @Test
    void create_failure_doesNotIncrementCounter() {
        // 库存不足:create 在第 1 步(商品校验)抛 DomainException,根本走不到 counter 累加点
        loginAs("u1", com.seafood.shared.security.Role.CUSTOMER);
        CartDocument cartDoc = new CartDocument();
        cartDoc.setUserId("u1");
        cartDoc.setItems(List.of(new CartItem("p1", 5, true, Instant.now())));
        when(cartRepo.findById("u1")).thenReturn(Optional.of(cartDoc));
        when(productRepo.findAllById(List.of("p1"))).thenReturn(List.of(activeProduct("p1", "三文鱼", 2)));
        when(productRepo.findById("p1")).thenReturn(Optional.of(activeProduct("p1", "三文鱼", 2)));

        assertThatThrownBy(() -> service.create("u1", "wechat"))
                .isInstanceOf(DomainException.class);

        assertThat(meterRegistry.find("orders.created").counters())
                .as("下单失败 → orders.created 任何 series 都不应被创建")
                .isEmpty();
    }

    // === PR #3 3.4:orders.cancelled counter 埋点(3 个 reason 各自 +1)===

    @Test
    void cancel_userReason_incrementsOrdersCancelledWithUserTag() {
        cancelAndAssertReason("user", "user");
    }

    @Test
    void cancel_timeoutReason_incrementsOrdersCancelledWithTimeoutTag() {
        cancelAndAssertReason("timeout", "timeout");
    }

    @Test
    void cancel_adminReason_incrementsOrdersCancelledWithAdminTag() {
        cancelAndAssertReason("admin", "admin");
    }

    @Test
    void cancel_arbitraryReason_collapsesToOtherTag() {
        // reason 是任意字符串:归 "other",防止高基数字符串污染 PromQL series
        cancelAndAssertReason("我不想要了", "other");
    }

    private void cancelAndAssertReason(String inputReason, String expectedTag) {
        loginAs("u1", com.seafood.shared.security.Role.CUSTOMER);
        OrderDocument doc = pendingOrder("o1", "u1", new BigDecimal("100.00"));
        when(orderRepo.findById("o1")).thenReturn(Optional.of(doc));
        when(orderRepo.save(any(OrderDocument.class))).thenAnswer(inv -> inv.getArgument(0));

        service.cancel("o1", inputReason);

        assertThat(meterRegistry.counter("orders.cancelled", "reason", expectedTag).count())
                .as("取消 reason=%s 应映射到 tag=%s", inputReason, expectedTag)
                .isEqualTo(1.0);
    }

    // === PR #3 3.5:orders.paid counter 埋点 + amountBucket 4 档分桶 ===

    @Test
    void markPaid_totalAmount350_classifiesAs100to500Bucket() {
        // 总金额 350 元 → amountBucket=100to500
        loginAs("u1", com.seafood.shared.security.Role.CUSTOMER);
        OrderDocument doc = pendingOrder("o1", "u1", new BigDecimal("350.00"));
        when(orderRepo.findById("o1")).thenReturn(Optional.of(doc));
        when(orderRepo.save(any(OrderDocument.class))).thenAnswer(inv -> inv.getArgument(0));

        service.markPaid("o1");

        assertThat(meterRegistry.counter("orders.paid",
                "paymentMethod", "wechat",
                "amountBucket", "100to500").count())
                .as("总金额 350 → orders.paid{wechat,100to500} += 1")
                .isEqualTo(1.0);
    }

    @Test
    void markPaid_amountBoundaries_classifyIntoCorrectBucket() {
        // 6 个 boundary 值,每个跑一遍 markPaid,断言落到 4 档的哪一档
        record Case(BigDecimal amount, String expectedBucket) {}
        List<Case> cases = List.of(
                new Case(new BigDecimal("99.99"),   "lt100"),
                new Case(new BigDecimal("100.00"),  "100to500"),
                new Case(new BigDecimal("499.99"),  "100to500"),
                new Case(new BigDecimal("500.00"),  "500to2000"),
                new Case(new BigDecimal("1999.99"), "500to2000"),
                new Case(new BigDecimal("2000.00"), "gte2000"));

        for (Case c : cases) {
            // 每个 case 独立 setup,counter 累加
            meterRegistry.clear();
            loginAs("u1", com.seafood.shared.security.Role.CUSTOMER);
            OrderDocument doc = pendingOrder("o1", "u1", c.amount());
            when(orderRepo.findById("o1")).thenReturn(Optional.of(doc));
            when(orderRepo.save(any(OrderDocument.class))).thenAnswer(inv -> inv.getArgument(0));

            service.markPaid("o1");

            assertThat(meterRegistry.counter("orders.paid",
                    "paymentMethod", "wechat",
                    "amountBucket", c.expectedBucket()).count())
                    .as("amount=%s → amountBucket=%s", c.amount(), c.expectedBucket())
                    .isEqualTo(1.0);
        }
    }

    // === helpers(仅 PR #3 测) ===

    private static OrderDocument pendingOrder(String orderId, String userId, BigDecimal totalAmount) {
        OrderDocument doc = new OrderDocument();
        doc.setId(orderId);
        doc.setUserId(userId);
        doc.setItems(List.of(new com.seafood.order.domain.OrderItem(
                "p1", "三文鱼", new BigDecimal("50.00"), 1)));
        doc.setTotalAmount(totalAmount);
        doc.setStatus("PENDING");
        doc.setCreatedAt(Instant.now());
        doc.setUpdatedAt(Instant.now());
        return doc;
    }
}
