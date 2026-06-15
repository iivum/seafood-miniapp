package com.seafood.order.domain;

import com.seafood.shared.error.DomainException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class OrderStatusTest {

    @Test
    void pendingCanGoToPaidOrCancelled() {
        OrderStatus p = new OrderStatus.Pending();
        assertThat(p.canTransitionTo(new OrderStatus.Paid())).isTrue();
        assertThat(p.canTransitionTo(new OrderStatus.Cancelled())).isTrue();
        assertThat(p.canTransitionTo(new OrderStatus.Shipped())).isFalse();
        assertThat(p.canTransitionTo(new OrderStatus.Completed())).isFalse();
    }

    @Test
    void paidCanGoToShippedOrCancelled() {
        OrderStatus p = new OrderStatus.Paid();
        assertThat(p.canTransitionTo(new OrderStatus.Shipped())).isTrue();
        assertThat(p.canTransitionTo(new OrderStatus.Cancelled())).isTrue();
        assertThat(p.canTransitionTo(new OrderStatus.Pending())).isFalse();
    }

    @Test
    void shippedOnlyGoesToCompleted() {
        OrderStatus s = new OrderStatus.Shipped();
        assertThat(s.canTransitionTo(new OrderStatus.Completed())).isTrue();
        assertThat(s.canTransitionTo(new OrderStatus.Cancelled())).isFalse();
    }

    @Test
    void completedIsTerminal() {
        OrderStatus c = new OrderStatus.Completed();
        assertThat(c.canTransitionTo(new OrderStatus.Paid())).isFalse();
        assertThat(c.canTransitionTo(new OrderStatus.Shipped())).isFalse();
        assertThat(c.canTransitionTo(new OrderStatus.Cancelled())).isFalse();
    }

    // ===== 路线图 4.7 / 4.8 退款状态机 =====

    @Test
    void completedCanGoToRefunding_4_7() {
        // 4.7:COMPLETED 订单(mp 申请退款)→ REFUNDING
        OrderStatus c = new OrderStatus.Completed();
        assertThat(c.canTransitionTo(new OrderStatus.Refunding())).isTrue();
    }

    @Test
    void refundingCanGoToRefundedOrCompleted_4_8() {
        // 4.8:REFUNDING → REFUNDED(admin 同意,终态) / REFUNDING → COMPLETED(admin 拒绝回退)
        OrderStatus r = new OrderStatus.Refunding();
        assertThat(r.canTransitionTo(new OrderStatus.Refunded())).isTrue();
        assertThat(r.canTransitionTo(new OrderStatus.Completed())).isTrue();
        // 其它都禁止
        assertThat(r.canTransitionTo(new OrderStatus.Cancelled())).isFalse();
        assertThat(r.canTransitionTo(new OrderStatus.Paid())).isFalse();
    }

    @Test
    void refundedIsTerminal() {
        OrderStatus r = new OrderStatus.Refunded();
        assertThat(r.canTransitionTo(new OrderStatus.Paid())).isFalse();
        assertThat(r.canTransitionTo(new OrderStatus.Cancelled())).isFalse();
        assertThat(r.canTransitionTo(new OrderStatus.Completed())).isFalse();
    }

    @Test
    void cancelledIsTerminal() {
        OrderStatus c = new OrderStatus.Cancelled();
        assertThat(c.canTransitionTo(new OrderStatus.Paid())).isFalse();
    }

    @Test
    void of_mapsAllValid() {
        assertThat(OrderStatus.of("PENDING")).isInstanceOf(OrderStatus.Pending.class);
        assertThat(OrderStatus.of("PAID")).isInstanceOf(OrderStatus.Paid.class);
        assertThat(OrderStatus.of("SHIPPED")).isInstanceOf(OrderStatus.Shipped.class);
        assertThat(OrderStatus.of("COMPLETED")).isInstanceOf(OrderStatus.Completed.class);
        assertThat(OrderStatus.of("CANCELLED")).isInstanceOf(OrderStatus.Cancelled.class);
        assertThat(OrderStatus.of("REFUNDING")).isInstanceOf(OrderStatus.Refunding.class);
        assertThat(OrderStatus.of("REFUNDED")).isInstanceOf(OrderStatus.Refunded.class);
    }

    @Test
    void of_rejectsUnknown() {
        assertThatThrownBy(() -> OrderStatus.of("PENDING-ISH"))
                .isInstanceOf(DomainException.class);
    }
}
