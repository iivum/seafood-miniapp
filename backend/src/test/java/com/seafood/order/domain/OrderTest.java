package com.seafood.order.domain;

import com.seafood.shared.error.DomainException;
import com.seafood.testsupport.builders.OrderBuilder;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class OrderTest {

    private final Instant t0 = Instant.parse("2026-06-01T00:00:00Z");
    private final OrderItem item = new OrderItem("p1", "三文鱼", new BigDecimal("99.00"), 2);

    private Order sample() {
        return OrderBuilder.anOrder()
                .withId("o1")
                .withUserId("u1")
                .withItems(List.of(item))
                .withTotalAmount(new BigDecimal("198.00"))
                .build();
    }

    @Test
    void markPaid_transitionsFromPending() {
        Order o = sample().markPaid(Instant.now());
        assertThat(o.status()).isInstanceOf(OrderStatus.Paid.class);
    }

    @Test
    void markShipped_transitionsFromPaid() {
        Order o = sample().markPaid(t0).markShipped(Instant.now());
        assertThat(o.status()).isInstanceOf(OrderStatus.Shipped.class);
    }

    @Test
    void ship_fromPending_throws() {
        assertThatThrownBy(() -> sample().markShipped(Instant.now()))
                .isInstanceOf(DomainException.class)
                .hasMessageContaining("PENDING");
    }

    @Test
    void ship_fromCompleted_throws() {
        Order c = sample().markPaid(t0).markShipped(t0).markCompleted(t0);
        assertThatThrownBy(() -> c.markShipped(Instant.now()))
                .isInstanceOf(DomainException.class);
    }

    @Test
    void cancel_fromPending_recordsReason() {
        Order o = sample().cancel("不再需要", Instant.now());
        assertThat(o.status()).isInstanceOf(OrderStatus.Cancelled.class);
        assertThat(o.cancelReason()).isEqualTo("不再需要");
    }

    @Test
    void cancel_fromShipped_throws() {
        Order s = sample().markPaid(t0).markShipped(t0);
        assertThatThrownBy(() -> s.cancel("晚了", Instant.now()))
                .isInstanceOf(DomainException.class)
                .hasMessageContaining("PENDING/PAID");
    }

    @Test
    void cancel_fromCompleted_throws() {
        Order c = sample().markPaid(t0).markShipped(t0).markCompleted(t0);
        assertThatThrownBy(() -> c.cancel("晚了", Instant.now()))
                .isInstanceOf(DomainException.class);
    }

    // ===== 路线图 4.7 / 4.8 退款命名方法 =====

    @Test
    void markRefunding_transitionsFromCompleted() {
        // 4.7:COMPLETED → REFUNDING
        Order c = sample().markPaid(t0).markShipped(t0).markCompleted(t0);
        Order r = c.markRefunding(t0);
        assertThat(r.status()).isInstanceOf(OrderStatus.Refunding.class);
    }

    @Test
    void markRefunding_fromPending_throws() {
        // PENDING 未付款不能申请退款(应在 Service 层拦;这里验证状态机兜底)
        assertThatThrownBy(() -> sample().markRefunding(Instant.now()))
                .isInstanceOf(DomainException.class)
                .hasMessageContaining("REFUNDING");
    }

    @Test
    void markRefunded_transitionsFromRefunding() {
        // 4.8 admin 同意:REFUNDING → REFUNDED
        Order r = sample().markPaid(t0).markShipped(t0).markCompleted(t0).markRefunding(t0);
        Order refunded = r.markRefunded(t0);
        assertThat(refunded.status()).isInstanceOf(OrderStatus.Refunded.class);
    }

    @Test
    void markRefundRejected_fallsBackToCompleted() {
        // 4.8 admin 拒绝:REFUNDING → COMPLETED(不是 CANCELLED,业务含义不同)
        Order r = sample().markPaid(t0).markShipped(t0).markCompleted(t0).markRefunding(t0);
        Order rolledBack = r.markRefundRejected(t0);
        assertThat(rolledBack.status()).isInstanceOf(OrderStatus.Completed.class);
    }

    @Test
    void markRefunded_fromCompleted_throws() {
        // COMPLETED 不能直接跳 REFUNDED(必须先转 REFUNDING)
        Order c = sample().markPaid(t0).markShipped(t0).markCompleted(t0);
        assertThatThrownBy(() -> c.markRefunded(Instant.now()))
                .isInstanceOf(DomainException.class);
    }

    @Test
    void constructor_rejectsEmptyItems() {
        // 该路径在创建 OrderService 真实下单时也会自然触发,这里通过精简到
        // 单一断言(hasMessage)避免 assertj contains 子串比对时的边界问题
        DomainException ex = null;
        try {
            new Order("o1", "u1", List.of(), new BigDecimal("1"),
                    new OrderStatus.Pending(), null, null, null, null, t0, t0);
        } catch (DomainException e) {
            ex = e;
        }
        assertThat(ex).isNotNull();
        assertThat(ex.getMessage()).isEqualTo("订单必须至少包含一行");
    }

    @Test
    void constructor_rejectsNonPositiveTotal() {
        assertThatThrownBy(() -> new Order("o1", "u1", List.of(item), BigDecimal.ZERO,
                new OrderStatus.Pending(), null, null, null, null, t0, t0))
                .isInstanceOf(DomainException.class)
                .hasMessageContaining("金额");
    }

    @Test
    void orderItem_subtotalMultiplies() {
        assertThat(item.subtotal()).isEqualByComparingTo("198.00");
    }
}
