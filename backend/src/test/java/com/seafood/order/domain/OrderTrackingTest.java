package com.seafood.order.domain;

import com.seafood.shared.error.DomainException;
import com.seafood.testsupport.builders.OrderBuilder;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 路线图 4.1 Order.tracking + TrackingEvent 值对象测试。
 *
 * <p>覆盖:
 * <ul>
 *   <li>TrackingEvent 不可空字段(description ≤ 200 字符,at 必填)</li>
 *   <li>OrderTracking 不可空 events 列表,carrier / trackingNumber 必填</li>
 *   <li>Order 构造时校验:tracking 非 null 时,status 必须是 SHIPPED / COMPLETED</li>
 *   <li>Order.attachTracking / clearTracking 命名方法</li>
 * </ul>
 */
class OrderTrackingTest {

    private final OrderItem item = new OrderItem("p1", "三文鱼", new java.math.BigDecimal("99.00"), 2);
    private final Instant t0 = Instant.parse("2026-06-01T00:00:00Z");

    private Order ship() {
        return OrderBuilder.anOrder()
                .withId("o1")
                .withUserId("u1")
                .withItems(List.of(item))
                .withTotalAmount(new java.math.BigDecimal("198.00"))
                .withCreatedAt(t0)
                .withUpdatedAt(t0)
                .build()
                .markPaid(t0)
                .markShipped(t0);
    }

    @Test
    void orderTracking_rejectsEmptyEvents() {
        assertThatThrownBy(() -> new OrderTracking("顺丰", "SF123", List.of()))
                .isInstanceOf(DomainException.class)
                .hasMessageContaining("物流事件流");
    }

    @Test
    void orderTracking_rejectsBlankCarrier() {
        assertThatThrownBy(() -> new OrderTracking("", "SF123",
                List.of(new TrackingEvent(Instant.now(), "SHIPPED", "上海", "已发货"))))
                .isInstanceOf(DomainException.class)
                .hasMessageContaining("物流公司");
    }

    @Test
    void orderTracking_rejectsBlankTrackingNumber() {
        assertThatThrownBy(() -> new OrderTracking("顺丰", "",
                List.of(new TrackingEvent(Instant.now(), "SHIPPED", "上海", "已发货"))))
                .isInstanceOf(DomainException.class)
                .hasMessageContaining("物流单号");
    }

    @Test
    void trackingEvent_rejectsNullAt() {
        assertThatThrownBy(() -> new TrackingEvent(null, "SHIPPED", "上海", "已发货"))
                .isInstanceOf(DomainException.class);
    }

    @Test
    void trackingEvent_rejectsDescriptionOver200() {
        String longDesc = "x".repeat(201);
        assertThatThrownBy(() -> new TrackingEvent(Instant.now(), "SHIPPED", "上海", longDesc))
                .isInstanceOf(DomainException.class)
                .hasMessageContaining("200");
    }

    @Test
    void order_attachTracking_onShipped() {
        Order s = ship();
        OrderTracking tracking = new OrderTracking("顺丰", "SF123",
                List.of(new TrackingEvent(Instant.now(), "SHIPPED", "上海", "已发货")));
        Order with = s.attachTracking(tracking);
        assertThat(with.tracking()).isEqualTo(tracking);
    }

    @Test
    void order_attachTracking_onCompleted() {
        Order s = ship().markCompleted(t0);
        OrderTracking tracking = new OrderTracking("顺丰", "SF123",
                List.of(new TrackingEvent(Instant.now(), "DELIVERED", "用户家", "已签收")));
        Order with = s.attachTracking(tracking);
        assertThat(with.tracking()).isEqualTo(tracking);
    }

    @Test
    void order_attachTracking_onPending_throws() {
        Order pending = OrderBuilder.anOrder()
                .withId("o1")
                .withUserId("u1")
                .withItems(List.of(item))
                .withTotalAmount(new java.math.BigDecimal("198.00"))
                .withCreatedAt(t0)
                .withUpdatedAt(t0)
                .build();
        OrderTracking tracking = new OrderTracking("顺丰", "SF123",
                List.of(new TrackingEvent(Instant.now(), "SHIPPED", "上海", "已发货")));
        assertThatThrownBy(() -> pending.attachTracking(tracking))
                .isInstanceOf(DomainException.class)
                .hasMessageContaining("SHIPPED");
    }

    @Test
    void order_constructorRejectsTrackingOnPending() {
        OrderTracking tracking = new OrderTracking("顺丰", "SF123",
                List.of(new TrackingEvent(Instant.now(), "SHIPPED", "上海", "已发货")));
        assertThatThrownBy(() -> new Order("o1", "u1", List.of(item),
                null, null, null, new java.math.BigDecimal("198.00"),
                new OrderStatus.Pending(), null, tracking, null, null, t0, t0))
                .isInstanceOf(DomainException.class)
                .hasMessageContaining("仅 SHIPPED");
    }

    @Test
    void order_clearTracking() {
        OrderTracking tracking = new OrderTracking("顺丰", "SF123",
                List.of(new TrackingEvent(Instant.now(), "SHIPPED", "上海", "已发货")));
        Order with = ship().attachTracking(tracking);
        assertThat(with.clearTracking().tracking()).isNull();
    }
}
