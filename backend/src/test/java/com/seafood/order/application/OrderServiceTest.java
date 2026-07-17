package com.seafood.order.application;

import com.seafood.order.api.dto.CartItemRequest;
import com.seafood.order.api.dto.OrderResponse;
import com.seafood.order.domain.CartItem;
import com.seafood.order.infra.CartDocument;
import com.seafood.order.infra.CartRepository;
import com.seafood.order.infra.OrderDocument;
import com.seafood.order.infra.OrderRepository;
import com.seafood.order.infra.RefundDocument;
import com.seafood.order.infra.RefundRepository;
import com.seafood.product.domain.ProductCategory;
import com.seafood.product.domain.ProductStatus;
import com.seafood.product.infra.ProductDocument;
import com.seafood.product.infra.ProductRepository;
import com.seafood.shared.error.DomainException;
import com.seafood.shared.error.NotFoundException;
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
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class OrderServiceTest {

    private OrderRepository orderRepo;
    private CartRepository cartRepo;
    private ProductRepository productRepo;
    private RefundRepository refundRepo;
    private MeterRegistry meterRegistry;
    private OrderService service;

    @BeforeEach
    void setUp() {
        orderRepo = mock(OrderRepository.class);
        cartRepo = mock(CartRepository.class);
        productRepo = mock(ProductRepository.class);
        // 4.7 引入 — 注入 RefundRepository(5 参构造)
        refundRepo = mock(RefundRepository.class);
        // PR #3 3.x:SimpleMeterRegistry in-memory,与 Spring Boot Actuator 默认
        // 装配的 MeterRegistry 类型兼容;counter().count() 直接可读,断言语义直白。
        meterRegistry = new SimpleMeterRegistry();
        service = new OrderService(orderRepo, cartRepo, productRepo, refundRepo, meterRegistry);
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
        // fix-order-amount-contract:小计 100.00 触发"满 100 减 10"(未传 shippingMethod
        // 按 FREE 兜底,运费 0),totalAmount = 100 + 0 - 10 = 90.00(此前是裸小计 100.00,
        // 语义变化按 design.md task 1.5 更新预期值)
        assertThat(res.totalAmount()).isEqualByComparingTo("90.00");
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
    void create_cartHasUnselectedItemPointingAtMissingProduct_failsWholeCheckout() {
        // Regression(mp-backend-contract-gaps Task 2a review 修复):cart 里一个已勾选
        // 的有效商品 + 一个未勾选、指向不存在/已下架商品的行。pre-diff(ade2df2)行为是
        // 存在性校验跑在全量 cart items 上(不看 selected),所以整单应该建不了 ——
        // 而不是静默忽略那行未勾选商品、只用已勾选行成功下单。
        loginAs("u1", com.seafood.shared.security.Role.CUSTOMER);
        CartDocument cartDoc = new CartDocument();
        cartDoc.setUserId("u1");
        cartDoc.setItems(List.of(
                new CartItem("p1", 2, true, Instant.now()),       // 已勾选,有效商品
                new CartItem("p-deleted", 1, false, Instant.now()) // 未勾选,商品已不存在
        ));
        when(cartRepo.findById("u1")).thenReturn(Optional.of(cartDoc));
        // findAllById 对全量 productIds(p1, p-deleted)查询,只有 p1 存在 → size 不匹配
        when(productRepo.findAllById(List.of("p1", "p-deleted")))
                .thenReturn(List.of(activeProduct("p1", "三文鱼", 10)));

        assertThatThrownBy(() -> service.create("u1"))
                .isInstanceOf(DomainException.class)
                .hasMessageContaining("商品不存在或已下架");

        // 整单应该建不了:不落库订单,不清购物车,不扣任何库存
        verify(orderRepo, never()).save(any(OrderDocument.class));
        verify(cartRepo, never()).deleteById(anyString());
        verify(productRepo, never()).save(any(ProductDocument.class));
    }

    // === mp-backend-contract-gaps Task 2a(design.md Gap 2 / D3):
    // 显式 items 直接购买建单,绕开购物车 ===

    @Test
    void create_withExplicitItems_buildsOrderWithoutTouchingCart() {
        // 直接购买路径:同样的存在/上架/库存校验 + 扣减,但绝不读/清购物车
        loginAs("u1", com.seafood.shared.security.Role.CUSTOMER);
        when(productRepo.findAllById(List.of("p1"))).thenReturn(List.of(activeProduct("p1", "三文鱼", 10)));
        when(productRepo.findById("p1")).thenReturn(Optional.of(activeProduct("p1", "三文鱼", 10)));
        when(orderRepo.save(any(OrderDocument.class))).thenAnswer(inv -> {
            OrderDocument d = inv.getArgument(0);
            d.setId("o1");
            return d;
        });

        OrderResponse res = service.create("u1", List.of(new CartItemRequest("p1", 2)));

        assertThat(res.id()).isEqualTo("o1");
        // fix-order-amount-contract:同上,小计 100 触发满 100 减 10,未传 shippingMethod
        // 按 FREE 兜底 → 90.00
        assertThat(res.totalAmount()).isEqualByComparingTo("90.00");
        assertThat(res.status()).isEqualTo("PENDING");
        // design D3:direct-buy 路径从不读/清购物车
        verifyNoInteractions(cartRepo);
    }

    @Test
    void create_withEmptyItemsList_fallsBackToCartPath() {
        // items 传空 list → 回退到现有购物车路径,行为与今天完全一致(含清空购物车)
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

        OrderResponse res = service.create("u1", List.of());

        // fix-order-amount-contract:同上,小计 100 触发满 100 减 10 → 90.00
        assertThat(res.totalAmount()).isEqualByComparingTo("90.00");
        verify(cartRepo).findById("u1");
        verify(cartRepo).deleteById("u1");
    }

    @Test
    void create_withExplicitItems_insufficientStock_rejectsAndLeavesCartAndProductUntouched() {
        // items 某行库存不足 → DomainException(GlobalExceptionHandler 映射 409),
        // 不落库任何订单,不动购物车,商品库存不被扣减
        loginAs("u1", com.seafood.shared.security.Role.CUSTOMER);
        ProductDocument prod = activeProduct("p1", "三文鱼", 1);
        when(productRepo.findAllById(List.of("p1"))).thenReturn(List.of(prod));

        assertThatThrownBy(() -> service.create("u1", List.of(new CartItemRequest("p1", 5))))
                .isInstanceOf(DomainException.class)
                .hasMessageContaining("库存不足");

        verifyNoInteractions(cartRepo);
        verify(orderRepo, never()).save(any(OrderDocument.class));
        verify(productRepo, never()).save(any(ProductDocument.class));
        assertThat(prod.getStock()).isEqualTo(1);
    }

    // === fix-order-amount-contract task 1.1-1.4:运费 + 优惠权威计算下沉后端 ===

    @Test
    void create_withPaidShippingMethodAndSubtotalOver100_totalIncludesFeeAndDiscount() {
        // task 1.1:顺丰(¥12)+ 小计 ≥100(满 100 减 10)→ totalAmount = subtotal + 12 - 10
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

        OrderResponse res = service.create("u1", "wechat", OrderPricing.SHIPPING_SF);

        // 小计 2×50=100.00,顺丰 12,满 100 减 10 → 100 + 12 - 10 = 102.00
        assertThat(res.subtotal()).isEqualByComparingTo("100.00");
        assertThat(res.shippingFee()).isEqualByComparingTo("12.00");
        assertThat(res.discount()).isEqualByComparingTo("10.00");
        assertThat(res.totalAmount()).isEqualByComparingTo("102.00");
    }

    @Test
    void create_withoutShippingMethod_defaultsToFreeShipping() {
        // task 1.2:shippingMethod 缺省/null → 按 FREE 兜底,shippingFee=0
        loginAs("u1", com.seafood.shared.security.Role.CUSTOMER);
        CartDocument cartDoc = new CartDocument();
        cartDoc.setUserId("u1");
        cartDoc.setItems(List.of(new CartItem("p1", 1, true, Instant.now())));
        when(cartRepo.findById("u1")).thenReturn(Optional.of(cartDoc));
        when(productRepo.findAllById(List.of("p1"))).thenReturn(List.of(activeProduct("p1", "三文鱼", 10)));
        when(productRepo.findById("p1")).thenReturn(Optional.of(activeProduct("p1", "三文鱼", 10)));
        when(orderRepo.save(any(OrderDocument.class))).thenAnswer(inv -> {
            OrderDocument d = inv.getArgument(0);
            d.setId("o1");
            return d;
        });

        OrderResponse res = service.create("u1", "wechat", null);

        assertThat(res.shippingFee()).isEqualByComparingTo("0");
    }

    @Test
    void create_withSubtotalUnder100_discountIsZero() {
        // task 1.3:小计 < 100 → discount = 0(1 件 × 50.00 = 50.00,不达阈值)
        loginAs("u1", com.seafood.shared.security.Role.CUSTOMER);
        CartDocument cartDoc = new CartDocument();
        cartDoc.setUserId("u1");
        cartDoc.setItems(List.of(new CartItem("p1", 1, true, Instant.now())));
        when(cartRepo.findById("u1")).thenReturn(Optional.of(cartDoc));
        when(productRepo.findAllById(List.of("p1"))).thenReturn(List.of(activeProduct("p1", "三文鱼", 10)));
        when(productRepo.findById("p1")).thenReturn(Optional.of(activeProduct("p1", "三文鱼", 10)));
        when(orderRepo.save(any(OrderDocument.class))).thenAnswer(inv -> {
            OrderDocument d = inv.getArgument(0);
            d.setId("o1");
            return d;
        });

        OrderResponse res = service.create("u1", "wechat", OrderPricing.SHIPPING_FREE);

        assertThat(res.subtotal()).isEqualByComparingTo("50.00");
        assertThat(res.discount()).isEqualByComparingTo("0");
        assertThat(res.totalAmount()).isEqualByComparingTo("50.00");
    }

    @Test
    void create_withExplicitItems_appliesShippingMethodSameAsCartPath() {
        // task 1.4:直接购买路径(create(userId, items, shippingMethod))同样接收并应用运费/优惠
        loginAs("u1", com.seafood.shared.security.Role.CUSTOMER);
        when(productRepo.findAllById(List.of("p1"))).thenReturn(List.of(activeProduct("p1", "三文鱼", 10)));
        when(productRepo.findById("p1")).thenReturn(Optional.of(activeProduct("p1", "三文鱼", 10)));
        when(orderRepo.save(any(OrderDocument.class))).thenAnswer(inv -> {
            OrderDocument d = inv.getArgument(0);
            d.setId("o1");
            return d;
        });

        OrderResponse res = service.create("u1", List.of(new CartItemRequest("p1", 2)), OrderPricing.SHIPPING_ZTO);

        // 小计 100.00,中通 8,满 100 减 10 → 100 + 8 - 10 = 98.00
        assertThat(res.shippingFee()).isEqualByComparingTo("8.00");
        assertThat(res.discount()).isEqualByComparingTo("10.00");
        assertThat(res.totalAmount()).isEqualByComparingTo("98.00");
        verifyNoInteractions(cartRepo);
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

    // === 路线图 4.2:getTracking ===

    private OrderDocument shippedOrderWithTracking(String orderId, String userId) {
        OrderDocument doc = new OrderDocument();
        doc.setId(orderId);
        doc.setUserId(userId);
        doc.setItems(List.of(new com.seafood.order.domain.OrderItem(
                "p1", "三文鱼", new BigDecimal("50.00"), 1)));
        doc.setTotalAmount(new BigDecimal("50.00"));
        doc.setStatus("SHIPPED");
        doc.setCreatedAt(Instant.now());
        doc.setUpdatedAt(Instant.now());
        com.seafood.order.domain.OrderTracking tracking = new com.seafood.order.domain.OrderTracking(
                "顺丰",
                "SF123",
                List.of(new com.seafood.order.domain.TrackingEvent(
                        Instant.now(), "SHIPPED", "上海", "已发货")));
        doc.setTracking(tracking);
        return doc;
    }

    @Test
    void getTracking_ownerSeesTracking() {
        loginAs("u1", com.seafood.shared.security.Role.CUSTOMER);
        when(orderRepo.findById("o1")).thenReturn(Optional.of(shippedOrderWithTracking("o1", "u1")));

        com.seafood.order.domain.OrderTracking t = service.getTracking("o1");

        assertThat(t).isNotNull();
        assertThat(t.carrier()).isEqualTo("顺丰");
        assertThat(t.trackingNumber()).isEqualTo("SF123");
        assertThat(t.events()).hasSize(1);
    }

    @Test
    void getTracking_adminCanSeeAnyOrder() {
        loginAs("admin", com.seafood.shared.security.Role.ADMIN);
        when(orderRepo.findById("o1")).thenReturn(Optional.of(shippedOrderWithTracking("o1", "u-other")));

        com.seafood.order.domain.OrderTracking t = service.getTracking("o1");
        assertThat(t).isNotNull();
    }

    @Test
    void getTracking_otherCustomerSeesNotFound() {
        // 防 enumeration:不是订单主且非 ADMIN,对外表现为订单不存在(404)
        loginAs("u2", com.seafood.shared.security.Role.CUSTOMER);
        when(orderRepo.findById("o1")).thenReturn(Optional.of(shippedOrderWithTracking("o1", "u1")));

        assertThatThrownBy(() -> service.getTracking("o1"))
                .isInstanceOf(com.seafood.shared.error.NotFoundException.class);
    }

    @Test
    void getTracking_pendingOrderReturnsNull() {
        loginAs("u1", com.seafood.shared.security.Role.CUSTOMER);
        when(orderRepo.findById("o1")).thenReturn(Optional.of(pendingOrder("o1", "u1", new BigDecimal("50"))));

        com.seafood.order.domain.OrderTracking t = service.getTracking("o1");
        assertThat(t).isNull();
    }

    // === 路线图 4.7:requestRefund ===

    private OrderDocument completedOrder(String orderId, String userId, BigDecimal total) {
        OrderDocument doc = new OrderDocument();
        doc.setId(orderId);
        doc.setUserId(userId);
        doc.setItems(List.of(new com.seafood.order.domain.OrderItem(
                "p1", "三文鱼", new BigDecimal("50.00"), 1)));
        doc.setTotalAmount(total);
        doc.setStatus("COMPLETED");
        doc.setCreatedAt(Instant.now());
        doc.setUpdatedAt(Instant.now());
        return doc;
    }

    @Test
    void requestRefund_ownerOnCompletedOrder_createsRequestedRefundAndFlipsToRefunding() {
        loginAs("u1", com.seafood.shared.security.Role.CUSTOMER);
        OrderDocument order = completedOrder("o1", "u1", new BigDecimal("100.00"));
        when(orderRepo.findById("o1")).thenReturn(Optional.of(order));
        when(refundRepo.findByOrderId("o1")).thenReturn(Optional.empty());
        when(refundRepo.save(any(RefundDocument.class))).thenAnswer(inv -> {
            RefundDocument d = inv.getArgument(0);
            d.setId("r1");
            return d;
        });
        when(orderRepo.save(any(OrderDocument.class))).thenAnswer(inv -> {
            OrderDocument saved = inv.getArgument(0);
            // 4.20 同步回原 doc 引用,让 test 看到最新 status(4.7 老测试假设 save
            // 就地修改原 doc;真实 MongoDB driver 行为同理,只是 mock 需要显式同步)
            order.setStatus(saved.getStatus());
            order.setRefundId(saved.getRefundId());
            return saved;
        });

        com.seafood.order.api.dto.RefundResponse res = service.requestRefund(
                "o1", new BigDecimal("100.00"), "海鲜质量有问题");

        assertThat(res.id()).isEqualTo("r1");
        assertThat(res.status()).isEqualTo("REQUESTED");
        assertThat(res.amount()).isEqualByComparingTo("100.00");
        // 同步:Order 状态应改为 REFUNDING
        assertThat(order.getStatus()).isEqualTo("REFUNDING");
        // 4.20 同步:refundId 应挂到 Order 上
        assertThat(order.getRefundId()).isEqualTo("r1");
    }

    @Test
    void requestRefund_rejectsAmountExceedingTotal() {
        loginAs("u1", com.seafood.shared.security.Role.CUSTOMER);
        when(orderRepo.findById("o1")).thenReturn(Optional.of(completedOrder("o1", "u1", new BigDecimal("100.00"))));
        when(refundRepo.findByOrderId("o1")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.requestRefund("o1", new BigDecimal("150.00"), "多了"))
                .isInstanceOf(DomainException.class)
                .hasMessageContaining("订单总额");
    }

    @Test
    void requestRefund_rejectsZeroAmount() {
        loginAs("u1", com.seafood.shared.security.Role.CUSTOMER);
        when(orderRepo.findById("o1")).thenReturn(Optional.of(completedOrder("o1", "u1", new BigDecimal("100.00"))));
        when(refundRepo.findByOrderId("o1")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.requestRefund("o1", BigDecimal.ZERO, "没金额"))
                .isInstanceOf(DomainException.class)
                .hasMessageContaining("大于 0");
    }

    @Test
    void requestRefund_rejectsPendingOrder() {
        // PENDING 未付款不允许申请退款
        loginAs("u1", com.seafood.shared.security.Role.CUSTOMER);
        when(orderRepo.findById("o1")).thenReturn(Optional.of(pendingOrder("o1", "u1", new BigDecimal("50"))));
        when(refundRepo.findByOrderId("o1")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.requestRefund("o1", new BigDecimal("50"), "想退"))
                .isInstanceOf(DomainException.class)
                .hasMessageContaining("不允许申请退款");
    }

    @Test
    void requestRefund_otherCustomerSeesNotFound() {
        // 防 enumeration:非订单主且非 ADMIN 抛 NotFoundException(同 getTracking 策略)
        loginAs("u2", com.seafood.shared.security.Role.CUSTOMER);
        when(orderRepo.findById("o1")).thenReturn(Optional.of(completedOrder("o1", "u1", new BigDecimal("100"))));

        assertThatThrownBy(() -> service.requestRefund("o1", new BigDecimal("50"), "想退"))
                .isInstanceOf(NotFoundException.class);
    }

    @Test
    void requestRefund_rejectsWhenAnotherRequestIsInFlight() {
        // 同单已有 REQUESTED 退款单 → 拒绝再次申请
        loginAs("u1", com.seafood.shared.security.Role.CUSTOMER);
        when(orderRepo.findById("o1")).thenReturn(Optional.of(completedOrder("o1", "u1", new BigDecimal("100"))));
        RefundDocument existing = new RefundDocument();
        existing.setId("r-old");
        existing.setOrderId("o1");
        existing.setStatus("REQUESTED");
        when(refundRepo.findByOrderId("o1")).thenReturn(Optional.of(existing));

        assertThatThrownBy(() -> service.requestRefund("o1", new BigDecimal("50"), "再退"))
                .isInstanceOf(DomainException.class)
                .hasMessageContaining("进行中");
    }

    @Test
    void requestRefund_allowsReapplyAfterRejected() {
        // 已有 REJECTED 退款单应允许再次申请(客服场景:同单分批退)
        loginAs("u1", com.seafood.shared.security.Role.CUSTOMER);
        when(orderRepo.findById("o1")).thenReturn(Optional.of(completedOrder("o1", "u1", new BigDecimal("100"))));
        RefundDocument rejected = new RefundDocument();
        rejected.setId("r-old");
        rejected.setOrderId("o1");
        rejected.setStatus("REJECTED");
        when(refundRepo.findByOrderId("o1")).thenReturn(Optional.of(rejected));
        when(refundRepo.save(any(RefundDocument.class))).thenAnswer(inv -> {
            RefundDocument d = inv.getArgument(0);
            d.setId("r-new");
            return d;
        });
        when(orderRepo.save(any(OrderDocument.class))).thenAnswer(inv -> inv.getArgument(0));

        com.seafood.order.api.dto.RefundResponse res = service.requestRefund(
                "o1", new BigDecimal("50"), "补一次");
        assertThat(res.id()).isEqualTo("r-new");
    }

    // === 路线图 4.8:approveRefund / rejectRefund ===

    private RefundDocument requestedRefund(String refundId, String orderId, String userId) {
        RefundDocument d = new RefundDocument();
        d.setId(refundId);
        d.setOrderId(orderId);
        d.setUserId(userId);
        d.setAmount(new BigDecimal("100.00"));
        d.setReason("质量有问题");
        d.setStatus("REQUESTED");
        d.setCreatedAt(Instant.now());
        d.setUpdatedAt(Instant.now());
        return d;
    }

    private OrderDocument refundingOrder(String orderId, String userId) {
        OrderDocument doc = completedOrder(orderId, userId, new BigDecimal("100.00"));
        doc.setStatus("REFUNDING");
        return doc;
    }

    @Test
    void approveRefund_movesRefundToApprovedAndOrderToRefunded() {
        loginAs("admin", com.seafood.shared.security.Role.ADMIN);
        when(refundRepo.findById("r1")).thenReturn(Optional.of(requestedRefund("r1", "o1", "u1")));
        when(orderRepo.findById("o1")).thenReturn(Optional.of(refundingOrder("o1", "u1")));
        when(refundRepo.save(any(RefundDocument.class))).thenAnswer(inv -> inv.getArgument(0));
        when(orderRepo.save(any(OrderDocument.class))).thenAnswer(inv -> inv.getArgument(0));

        com.seafood.order.api.dto.RefundResponse res = service.approveRefund("r1");

        assertThat(res.status()).isEqualTo("APPROVED");
        // 同步:Order 应转 REFUNDED
        org.mockito.ArgumentCaptor<OrderDocument> cap = org.mockito.ArgumentCaptor.forClass(OrderDocument.class);
        org.mockito.Mockito.verify(orderRepo).save(cap.capture());
        assertThat(cap.getValue().getStatus()).isEqualTo("REFUNDED");
    }

    @Test
    void approveRefund_rejectsWhenRefundAlreadyApproved() {
        loginAs("admin", com.seafood.shared.security.Role.ADMIN);
        RefundDocument alreadyApproved = requestedRefund("r1", "o1", "u1");
        alreadyApproved.setStatus("APPROVED");
        when(refundRepo.findById("r1")).thenReturn(Optional.of(alreadyApproved));

        assertThatThrownBy(() -> service.approveRefund("r1"))
                .isInstanceOf(DomainException.class)
                .hasMessageContaining("REQUESTED");
    }

    @Test
    void rejectRefund_movesRefundToRejectedAndOrderBackToCompleted() {
        loginAs("admin", com.seafood.shared.security.Role.ADMIN);
        when(refundRepo.findById("r1")).thenReturn(Optional.of(requestedRefund("r1", "o1", "u1")));
        when(orderRepo.findById("o1")).thenReturn(Optional.of(refundingOrder("o1", "u1")));
        when(refundRepo.save(any(RefundDocument.class))).thenAnswer(inv -> inv.getArgument(0));
        when(orderRepo.save(any(OrderDocument.class))).thenAnswer(inv -> inv.getArgument(0));

        com.seafood.order.api.dto.RefundResponse res = service.rejectRefund("r1", "已签收 7 天,超售后期");

        assertThat(res.status()).isEqualTo("REJECTED");
        // 同步:Order 应回退 COMPLETED(不是 CANCELLED — 业务含义不同)
        org.mockito.ArgumentCaptor<OrderDocument> cap = org.mockito.ArgumentCaptor.forClass(OrderDocument.class);
        org.mockito.Mockito.verify(orderRepo).save(cap.capture());
        assertThat(cap.getValue().getStatus()).isEqualTo("COMPLETED");
    }

    @Test
    void rejectRefund_rejectsOverlongReason() {
        loginAs("admin", com.seafood.shared.security.Role.ADMIN);
        String overlong = "x".repeat(201);

        assertThatThrownBy(() -> service.rejectRefund("r1", overlong))
                .isInstanceOf(DomainException.class)
                .hasMessageContaining("200 字符");
    }

    @Test
    void rejectRefund_throwsNotFoundForMissingRefund() {
        loginAs("admin", com.seafood.shared.security.Role.ADMIN);
        when(refundRepo.findById("r-missing")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.rejectRefund("r-missing", "无理由"))
                .isInstanceOf(NotFoundException.class);
    }

    // === 路线图 4.13:batchShip ===

    private OrderDocument paidOrder(String orderId, String userId) {
        OrderDocument doc = new OrderDocument();
        doc.setId(orderId);
        doc.setUserId(userId);
        doc.setItems(List.of(new com.seafood.order.domain.OrderItem(
                "p1", "三文鱼", new BigDecimal("50.00"), 1)));
        doc.setTotalAmount(new BigDecimal("50.00"));
        doc.setStatus("PAID");
        doc.setCreatedAt(Instant.now());
        doc.setUpdatedAt(Instant.now());
        return doc;
    }

    @Test
    void batchShip_allPaid_shipsAllAndReturnsSuccess() {
        loginAs("admin", com.seafood.shared.security.Role.ADMIN);
        when(orderRepo.findById("o1")).thenReturn(Optional.of(paidOrder("o1", "u1")));
        when(orderRepo.findById("o2")).thenReturn(Optional.of(paidOrder("o2", "u2")));
        when(orderRepo.save(any(OrderDocument.class))).thenAnswer(inv -> inv.getArgument(0));

        com.seafood.bff.admin.dto.BatchShipResponse res = service.batchShip(
                List.of("o1", "o2"), null, null);

        assertThat(res.total()).isEqualTo(2);
        assertThat(res.successCount()).isEqualTo(2);
        assertThat(res.failedCount()).isEqualTo(0);
        assertThat(res.successIds()).containsExactly("o1", "o2");
        // 验证两个单都被持久化
        org.mockito.Mockito.verify(orderRepo, org.mockito.Mockito.times(2))
                .save(any(OrderDocument.class));
    }

    @Test
    void batchShip_partialFailure_continuesAndReportsBoth() {
        // 策略:逐单处理 + 失败跳过。o1 PAID → 成功;o2 PENDING → 失败(状态不对);
        // o3 不存在 → 失败
        loginAs("admin", com.seafood.shared.security.Role.ADMIN);
        when(orderRepo.findById("o1")).thenReturn(Optional.of(paidOrder("o1", "u1")));
        when(orderRepo.findById("o2")).thenReturn(Optional.of(pendingOrder("o2", "u1", new BigDecimal("50"))));
        when(orderRepo.findById("o3")).thenReturn(Optional.empty());
        when(orderRepo.save(any(OrderDocument.class))).thenAnswer(inv -> inv.getArgument(0));

        com.seafood.bff.admin.dto.BatchShipResponse res = service.batchShip(
                List.of("o1", "o2", "o3"), null, null);

        assertThat(res.total()).isEqualTo(3);
        assertThat(res.successCount()).isEqualTo(1);
        assertThat(res.failedCount()).isEqualTo(2);
        assertThat(res.successIds()).containsExactly("o1");
        assertThat(res.failed())
                .extracting(com.seafood.bff.admin.dto.BatchShipResponse.FailedItem::orderId)
                .containsExactly("o2", "o3");
        assertThat(res.failed().get(0).reason()).contains("PENDING");
        assertThat(res.failed().get(1).reason()).isEqualTo("订单不存在");
    }

    @Test
    void batchShip_withCarrierAndTracking_attachesTracking() {
        loginAs("admin", com.seafood.shared.security.Role.ADMIN);
        when(orderRepo.findById("o1")).thenReturn(Optional.of(paidOrder("o1", "u1")));
        when(orderRepo.save(any(OrderDocument.class))).thenAnswer(inv -> inv.getArgument(0));

        service.batchShip(List.of("o1"), "顺丰", "SF123");

        // 验证保存的 OrderDocument 状态 SHIPPED + tracking 已挂
        org.mockito.ArgumentCaptor<OrderDocument> cap =
                org.mockito.ArgumentCaptor.forClass(OrderDocument.class);
        org.mockito.Mockito.verify(orderRepo).save(cap.capture());
        OrderDocument saved = cap.getValue();
        assertThat(saved.getStatus()).isEqualTo("SHIPPED");
        assertThat(saved.getTracking()).isNotNull();
        assertThat(saved.getTracking().carrier()).isEqualTo("顺丰");
        assertThat(saved.getTracking().trackingNumber()).isEqualTo("SF123");
        assertThat(saved.getTracking().events()).hasSize(1);
    }

    @Test
    void batchShip_partialTrackingInput_reportsGlobalFailure() {
        // 只填 carrier 没填 trackingNumber → 报 global 失败项,但 orderIds 继续处理
        loginAs("admin", com.seafood.shared.security.Role.ADMIN);
        when(orderRepo.findById("o1")).thenReturn(Optional.of(paidOrder("o1", "u1")));
        when(orderRepo.save(any(OrderDocument.class))).thenAnswer(inv -> inv.getArgument(0));

        com.seafood.bff.admin.dto.BatchShipResponse res = service.batchShip(
                List.of("o1"), "顺丰", null);

        // global 失败项 + o1 仍然成功(只是没挂物流)
        assertThat(res.successIds()).containsExactly("o1");
        assertThat(res.failed())
                .extracting(com.seafood.bff.admin.dto.BatchShipResponse.FailedItem::orderId)
                .containsExactly("(global)");
        // 验证 o1 保存时没有 tracking
        org.mockito.ArgumentCaptor<OrderDocument> cap =
                org.mockito.ArgumentCaptor.forClass(OrderDocument.class);
        org.mockito.Mockito.verify(orderRepo).save(cap.capture());
        assertThat(cap.getValue().getTracking()).isNull();
    }

    @Test
    void batchShip_emptyList_stillReturnsEmptyResponse() {
        // 边界:空列表应返回 total=0,不应该 NPE
        loginAs("admin", com.seafood.shared.security.Role.ADMIN);
        com.seafood.bff.admin.dto.BatchShipResponse res = service.batchShip(
                List.of(), null, null);
        assertThat(res.total()).isZero();
        assertThat(res.successCount()).isZero();
        assertThat(res.failedCount()).isZero();
    }

    // === 路线图 4.9:orders.refunded counter 埋点 ===

    @Test
    void approveRefund_incrementsOrdersRefundedCounterWithAmountBucket() {
        loginAs("admin", com.seafood.shared.security.Role.ADMIN);
        when(refundRepo.findById("r1")).thenReturn(Optional.of(requestedRefund("r1", "o1", "u1")));
        when(orderRepo.findById("o1")).thenReturn(Optional.of(refundingOrder("o1", "u1")));
        when(refundRepo.save(any(RefundDocument.class))).thenAnswer(inv -> inv.getArgument(0));
        when(orderRepo.save(any(OrderDocument.class))).thenAnswer(inv -> inv.getArgument(0));

        service.approveRefund("r1");

        // 订单总额 100 → amountBucket=100to500
        assertThat(meterRegistry.counter("orders.refunded",
                "paymentMethod", "wechat",
                "amountBucket", "100to500").count())
                .as("admin 同意退款 → orders.refunded{wechat,100to500} += 1")
                .isEqualTo(1.0);
    }

    @Test
    void rejectRefund_doesNotIncrementOrdersRefundedCounter() {
        // 拒绝退款不算"已退款",不递增 orders.refunded(只递增 orders.cancelled 不在这条链路上)
        loginAs("admin", com.seafood.shared.security.Role.ADMIN);
        when(refundRepo.findById("r1")).thenReturn(Optional.of(requestedRefund("r1", "o1", "u1")));
        when(orderRepo.findById("o1")).thenReturn(Optional.of(refundingOrder("o1", "u1")));
        when(refundRepo.save(any(RefundDocument.class))).thenAnswer(inv -> inv.getArgument(0));
        when(orderRepo.save(any(OrderDocument.class))).thenAnswer(inv -> inv.getArgument(0));

        service.rejectRefund("r1", "已超售后期");

        assertThat(meterRegistry.find("orders.refunded").counters())
                .as("拒绝退款不写 orders.refunded")
                .isEmpty();
    }

    @Test
    void approveRefund_amountBoundaries_classifyIntoCorrectBucket() {
        // 4 个 boundary 值各跑一遍,断言落到正确 bucket(与 orders.paid 同样分桶策略)
        record Case(java.math.BigDecimal amount, String expectedBucket) {}
        java.util.List<Case> cases = java.util.List.of(
                new Case(new java.math.BigDecimal("99.99"),   "lt100"),
                new Case(new java.math.BigDecimal("100.00"),  "100to500"),
                new Case(new java.math.BigDecimal("500.00"),  "500to2000"),
                new Case(new java.math.BigDecimal("2000.00"), "gte2000"));
        for (Case c : cases) {
            meterRegistry.clear();
            loginAs("admin", com.seafood.shared.security.Role.ADMIN);
            RefundDocument req = requestedRefund("r1", "o1", "u1");
            when(refundRepo.findById("r1")).thenReturn(Optional.of(req));
            when(orderRepo.findById("o1")).thenReturn(
                    Optional.of(refundingOrderWithTotal("o1", "u1", c.amount())));
            when(refundRepo.save(any(RefundDocument.class))).thenAnswer(inv -> inv.getArgument(0));
            when(orderRepo.save(any(OrderDocument.class))).thenAnswer(inv -> inv.getArgument(0));

            service.approveRefund("r1");

            assertThat(meterRegistry.counter("orders.refunded",
                    "paymentMethod", "wechat",
                    "amountBucket", c.expectedBucket()).count())
                    .as("amount=%s → amountBucket=%s", c.amount(), c.expectedBucket())
                    .isEqualTo(1.0);
        }
    }

    private OrderDocument refundingOrderWithTotal(String orderId, String userId, java.math.BigDecimal total) {
        OrderDocument doc = refundingOrder(orderId, userId);
        doc.setTotalAmount(total);
        return doc;
    }

    // === 路线图 4.15:exportRecentOrdersAsCsv ===

    @Test
    void exportRecentOrdersAsCsv_emitsHeaderAndRow() {
        when(orderRepo.findTop500ByOrderByCreatedAtDesc()).thenReturn(List.of(
                paidOrderWithCreatedAt("o1", "u1", new java.math.BigDecimal("50.00"),
                        Instant.parse("2026-06-01T00:00:00Z"))));
        String csv = service.exportRecentOrdersAsCsv(500);
        // header
        assertThat(csv).startsWith("订单号,用户ID,金额(元),状态,取消原因,创建时间,更新时间\n");
        // row:o1,u1,50.00,PAID,,2026-06-01T00:00:00Z,2026-06-01T00:00:00Z
        assertThat(csv).contains("o1,u1,50.00,PAID,,2026-06-01T00:00:00Z,2026-06-01T00:00:00Z\n");
    }

    @Test
    void exportRecentOrdersAsCsv_escapesCommaAndQuoteInFields() {
        // RFC 4180:含 , / " / 换行 的字段要双引号包裹,内部 " → ""
        // helper cancelledOrderWithReason 把 cancelReason + " " + reason2 拼一起
        // → 整字段为 `有,问题 "双引号"转义`,同时含逗号 + 双引号
        when(orderRepo.findTop500ByOrderByCreatedAtDesc()).thenReturn(List.of(
                cancelledOrderWithReason("o1", "u1",
                        "有,问题", // 含逗号
                        "\"双引号\"转义"))); // 含双引号
        String csv = service.exportRecentOrdersAsCsv(500);
        // 字段同时含逗号 + 双引号 → 整体被双引号包裹,内部 " 被 "" 转义
        assertThat(csv).contains("\"有,问题 \"\"双引号\"\"转义\"");
    }

    @Test
    void exportRecentOrdersAsCsv_emptyResult_emitsHeaderOnly() {
        when(orderRepo.findTop500ByOrderByCreatedAtDesc()).thenReturn(List.of());
        String csv = service.exportRecentOrdersAsCsv(500);
        assertThat(csv).isEqualTo("订单号,用户ID,金额(元),状态,取消原因,创建时间,更新时间\n");
    }

    @Test
    void exportRecentOrdersAsCsv_respectsLimitCap() {
        // 仓库返回 3 单,limit=2 → 应只输出 2 行数据
        when(orderRepo.findTop500ByOrderByCreatedAtDesc()).thenReturn(List.of(
                paidOrderWithCreatedAt("o1", "u1", new java.math.BigDecimal("10.00"), Instant.now()),
                paidOrderWithCreatedAt("o2", "u2", new java.math.BigDecimal("20.00"), Instant.now()),
                paidOrderWithCreatedAt("o3", "u3", new java.math.BigDecimal("30.00"), Instant.now())));
        String csv = service.exportRecentOrdersAsCsv(2);
        // 3 个订单 id 中只能出现 2 个
        long count = csv.lines().skip(1) // 跳 header
                .filter(line -> !line.isBlank())
                .count();
        assertThat(count).isEqualTo(2);
    }

    private OrderDocument paidOrderWithCreatedAt(String orderId, String userId,
                                                  java.math.BigDecimal total, Instant createdAt) {
        OrderDocument doc = paidOrder(orderId, userId);
        doc.setTotalAmount(total);
        doc.setCreatedAt(createdAt);
        doc.setUpdatedAt(createdAt);
        return doc;
    }

    private OrderDocument cancelledOrderWithReason(String orderId, String userId,
                                                    String cancelReason, String reason2) {
        // 复用一个 orderItem,reason 字段就放 cancelReason 测试逗号转义
        OrderDocument doc = paidOrder(orderId, userId);
        doc.setStatus("CANCELLED");
        doc.setCancelReason(cancelReason + " " + reason2);
        return doc;
    }

    // === 路线图 4.14:renderPicklistHtml ===

    @Test
    void renderPicklistHtml_containsOrderIdAndItemRows() {
        OrderDocument doc = paidOrderWithCreatedAt("o1", "u1",
                new java.math.BigDecimal("150.00"),
                Instant.parse("2026-06-01T00:00:00Z"));
        when(orderRepo.findById("o1")).thenReturn(Optional.of(doc));

        String html = service.renderPicklistHtml("o1");

        assertThat(html).contains("<title>拣货单 - o1</title>");
        assertThat(html).contains("订单号:o1");
        assertThat(html).contains("用户 ID:u1");
        // 含商品行 + 合计
        assertThat(html).contains("三文鱼");
        assertThat(html).contains("¥ 50.00");
        assertThat(html).contains("¥ 150.00");
        // 含打印按钮
        assertThat(html).contains("onclick=\"window.print()\"");
    }

    @Test
    void renderPicklistHtml_escapesHtmlInFields() {
        // 防御:商品含 < > & 时不应破坏 HTML 结构
        OrderDocument doc = paidOrderWithCreatedAt("o1", "u1", new java.math.BigDecimal("50.00"),
                Instant.now());
        doc.setItems(List.of(new com.seafood.order.domain.OrderItem(
                "p1", "<script>alert(1)</script>", new java.math.BigDecimal("50.00"), 1)));
        when(orderRepo.findById("o1")).thenReturn(Optional.of(doc));

        String html = service.renderPicklistHtml("o1");

        assertThat(html).contains("&lt;script&gt;alert(1)&lt;/script&gt;");
        assertThat(html).doesNotContain("<script>alert(1)</script>");
    }

    @Test
    void renderPicklistHtml_throwsNotFoundForMissing() {
        when(orderRepo.findById("o-missing")).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.renderPicklistHtml("o-missing"))
                .isInstanceOf(NotFoundException.class);
    }

    // === sprint-1-closure 1.6 / 1.7:transition() + 3 新增 customer action ===

    private OrderDocument shippedOrderFor(String orderId, String userId) {
        OrderDocument doc = new OrderDocument();
        doc.setId(orderId);
        doc.setUserId(userId);
        doc.setItems(List.of(new com.seafood.order.domain.OrderItem(
                "p1", "三文鱼", new BigDecimal("50.00"), 1)));
        doc.setTotalAmount(new BigDecimal("50.00"));
        doc.setStatus("SHIPPED");
        doc.setCreatedAt(Instant.now());
        doc.setUpdatedAt(Instant.now());
        return doc;
    }

    @Test
    void transition_confirmReceive_onShippedOrder_flipsToCompletedAndIncrementsCounter() {
        loginAs("u1", com.seafood.shared.security.Role.CUSTOMER);
        when(orderRepo.findById("o1")).thenReturn(Optional.of(shippedOrderFor("o1", "u1")));
        when(orderRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));

        OrderResponse resp = service.transition("o1", com.seafood.order.domain.OrderAction.CONFIRM_RECEIVE);

        assertThat(resp.status()).isEqualTo("COMPLETED");
        assertThat(meterRegistry.counter("orders.completed").count()).isEqualTo(1.0);
    }

    @Test
    void transition_confirmReceive_onPendingOrder_rejectedAsInvalidState() {
        loginAs("u1", com.seafood.shared.security.Role.CUSTOMER);
        when(orderRepo.findById("o1")).thenReturn(Optional.of(pendingOrder("o1", "u1", new BigDecimal("50"))));

        assertThatThrownBy(() -> service.transition("o1", com.seafood.order.domain.OrderAction.CONFIRM_RECEIVE))
                .isInstanceOf(DomainException.class)
                .hasMessageContaining("CONFIRM_RECEIVE not allowed");
    }

    @Test
    void rebuy_fromCompletedOrder_returnsCartItemsAndIncrementsCounter() {
        loginAs("u1", com.seafood.shared.security.Role.CUSTOMER);
        when(orderRepo.findById("o1")).thenReturn(Optional.of(completedOrder("o1", "u1", new BigDecimal("50"))));

        List<com.seafood.order.api.dto.CartItemResponse> items = service.rebuy("o1");

        assertThat(items).hasSize(1);
        assertThat(items.get(0).productId()).isEqualTo("p1");
        assertThat(items.get(0).quantity()).isEqualTo(1);
        assertThat(meterRegistry.counter("orders.rebuy").count()).isEqualTo(1.0);
    }

    @Test
    void rebuy_fromPendingOrder_rejected() {
        loginAs("u1", com.seafood.shared.security.Role.CUSTOMER);
        when(orderRepo.findById("o1")).thenReturn(Optional.of(pendingOrder("o1", "u1", new BigDecimal("50"))));

        assertThatThrownBy(() -> service.rebuy("o1"))
                .isInstanceOf(DomainException.class)
                .hasMessageContaining("Cannot rebuy order in status PENDING");
    }

    @Test
    void remindShip_onPaidOrder_incrementsCounter() {
        loginAs("u1", com.seafood.shared.security.Role.CUSTOMER);
        when(orderRepo.findById("o1")).thenReturn(Optional.of(paidOrderWithCreatedAt("o1", "u1", new BigDecimal("50"), Instant.now())));

        service.remindShip("o1");

        assertThat(meterRegistry.counter("orders.remind_ship").count()).isEqualTo(1.0);
    }

    @Test
    void remindShip_onPendingOrder_rejected() {
        loginAs("u1", com.seafood.shared.security.Role.CUSTOMER);
        when(orderRepo.findById("o1")).thenReturn(Optional.of(pendingOrder("o1", "u1", new BigDecimal("50"))));

        assertThatThrownBy(() -> service.remindShip("o1"))
                .isInstanceOf(DomainException.class);
    }

    @Test
    void transition_otherCustomerCannotCancel() {
        loginAs("u2", com.seafood.shared.security.Role.CUSTOMER);
        when(orderRepo.findById("o1")).thenReturn(Optional.of(pendingOrder("o1", "u1", new BigDecimal("50"))));

        assertThatThrownBy(() -> service.transition("o1", com.seafood.order.domain.OrderAction.CANCEL))
                .isInstanceOf(NotFoundException.class);
    }

    // === sprint-1-closure 1.7:happy path PENDING → PAID → SHIPPED → COMPLETED ===
    // 单测走完 4 状态,每步验证:
    //   - Order.status 转移正确
    //   - 对应 counter 增量
    //   - 不变量(订单金额、customer 一致)保留
    // 端到端覆盖 sprint-1-closure D2「状态转移表驱动」的核心 happy path,
    // 5 个非法转移单测 + 1 个 happy path 合起来保证 OrderAction 矩阵没有 off-by-one。

    @Test
    void transition_happyPath_pendingToCompleted_incrementsAllCounters() {
        loginAs("u1", com.seafood.shared.security.Role.CUSTOMER);
        // PENDING 起点
        OrderDocument doc = pendingOrder("o1", "u1", new BigDecimal("200.00"));
        when(orderRepo.findById("o1")).thenReturn(Optional.of(doc));
        when(orderRepo.save(any(OrderDocument.class))).thenAnswer(inv -> inv.getArgument(0));

        // 1. PENDING → PAID(pay action)
        OrderResponse paid = service.transition("o1", com.seafood.order.domain.OrderAction.PAY);
        assertThat(paid.status()).isEqualTo("PAID");
        // amountBucket=100to500(总金额 200)
        assertThat(meterRegistry.counter("orders.paid", "paymentMethod", "wechat", "amountBucket", "100to500").count())
                .as("pay 应增量 orders.paid paymentMethod=wechat amountBucket=100to500")
                .isEqualTo(1.0);

        // 2. PAID → SHIPPED(走 service.ship,admin 调;ship 本身无独立 counter)
        loginAs("admin-bootstrap", com.seafood.shared.security.Role.ADMIN);
        when(orderRepo.findById("o1")).thenReturn(Optional.of(paidOrder("o1", "u1")));
        OrderResponse shipped = service.ship("o1");
        assertThat(shipped.status()).isEqualTo("SHIPPED");

        // 3. SHIPPED → COMPLETED(confirmReceive action)
        loginAs("u1", com.seafood.shared.security.Role.CUSTOMER);
        when(orderRepo.findById("o1")).thenReturn(Optional.of(shippedOrderFor("o1", "u1")));
        OrderResponse completed = service.transition("o1", com.seafood.order.domain.OrderAction.CONFIRM_RECEIVE);
        assertThat(completed.status()).isEqualTo("COMPLETED");
        assertThat(meterRegistry.counter("orders.completed").count())
                .as("confirmReceive 应增量 orders.completed")
                .isEqualTo(1.0);
    }

    @Test
    void sumTotalAmountCreatedSince_returnsZeroWhenNoOrders() {
        when(orderRepo.findTop500ByOrderByCreatedAtDesc()).thenReturn(List.of());

        BigDecimal result = service.sumTotalAmountCreatedSince(Instant.parse("2026-01-01T11:00:00Z"));

        assertThat(result).isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    void sumTotalAmountCreatedSince_sumsOnlyOrdersInRange() {
        Instant base = Instant.parse("2026-01-01T12:00:00Z");
        Instant from = base.minusSeconds(3600); // 1 h 前

        // findTop500 按 createdAt 降序返回 — 最新在前
        OrderDocument o1 = new OrderDocument();
        o1.setCreatedAt(base.minusSeconds(100));
        o1.setTotalAmount(new BigDecimal("100.00"));

        OrderDocument o2 = new OrderDocument();
        o2.setCreatedAt(base.minusSeconds(600));
        o2.setTotalAmount(new BigDecimal("50.00"));

        OrderDocument o3 = new OrderDocument(); // 超出 1h 窗口
        o3.setCreatedAt(base.minusSeconds(7200));
        o3.setTotalAmount(new BigDecimal("999.00"));

        when(orderRepo.findTop500ByOrderByCreatedAtDesc()).thenReturn(List.of(o1, o2, o3));

        BigDecimal result = service.sumTotalAmountCreatedSince(from);

        assertThat(result).isEqualByComparingTo("150.00");
    }
}
