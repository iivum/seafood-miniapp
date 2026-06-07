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
    }

    @Test
    void of_rejectsUnknown() {
        assertThatThrownBy(() -> OrderStatus.of("PENDING-ISH"))
                .isInstanceOf(DomainException.class);
    }
}
