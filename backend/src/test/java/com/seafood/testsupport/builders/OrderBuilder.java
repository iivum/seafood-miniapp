package com.seafood.testsupport.builders;

import com.seafood.order.domain.Order;
import com.seafood.order.domain.OrderItem;
import com.seafood.order.domain.OrderStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/**
 * OrderBuilder — D1 test data builder (路线图 §3.2 Sprint 2 子项目 ①).
 *
 * <p>用法:`OrderBuilder.anOrder().withId("o1").withStatus(new OrderStatus.Paid()).build()`.
 * 核心字段覆盖(id / userId / items / totalAmount / status / createdAt / updatedAt);
 * 其他字段(cancelReason / tracking / refundId / estimatedDelivery)默认 null,需要时
 * 用 Order record 的 withXxx 命名方法链式补充(如 `builder.build().withEstimatedDelivery(...)`).
 *
 * <p>本类只放 test fixture,不进 main src — 不污染运行时,无 Spring / Lombok 依赖.
 */
public final class OrderBuilder {

    private static final Instant DEFAULT_T = Instant.parse("2026-06-01T00:00:00Z");

    private String id = "o-test";
    private String userId = "u-test";
    private List<OrderItem> items = List.of(
        new OrderItem("p-1", "三文鱼", new BigDecimal("99.00"), 2));
    // fix-order-amount-contract:默认 0(既有测试大多不关心运费/优惠明细,只断言
    // totalAmount);要测运费/优惠具体值时用 withSubtotal/withShippingFee/withDiscount。
    private BigDecimal subtotal = BigDecimal.ZERO;
    private BigDecimal shippingFee = BigDecimal.ZERO;
    private BigDecimal discount = BigDecimal.ZERO;
    private BigDecimal totalAmount = new BigDecimal("198.00");
    private OrderStatus status = new OrderStatus.Pending();
    private Instant createdAt = DEFAULT_T;
    private Instant updatedAt = DEFAULT_T;

    private OrderBuilder() {}

    public static OrderBuilder anOrder() {
        return new OrderBuilder();
    }

    public OrderBuilder withId(String id) { this.id = id; return this; }
    public OrderBuilder withUserId(String userId) { this.userId = userId; return this; }
    public OrderBuilder withItems(List<OrderItem> items) { this.items = items; return this; }
    public OrderBuilder withSubtotal(BigDecimal subtotal) { this.subtotal = subtotal; return this; }
    public OrderBuilder withShippingFee(BigDecimal shippingFee) { this.shippingFee = shippingFee; return this; }
    public OrderBuilder withDiscount(BigDecimal discount) { this.discount = discount; return this; }
    public OrderBuilder withTotalAmount(BigDecimal totalAmount) { this.totalAmount = totalAmount; return this; }
    public OrderBuilder withStatus(OrderStatus status) { this.status = status; return this; }
    public OrderBuilder withCreatedAt(Instant createdAt) { this.createdAt = createdAt; return this; }
    public OrderBuilder withUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; return this; }

    public Order build() {
        return new Order(id, userId, items, subtotal, shippingFee, discount, totalAmount, status,
            null, null, null, null, createdAt, updatedAt);
    }
}