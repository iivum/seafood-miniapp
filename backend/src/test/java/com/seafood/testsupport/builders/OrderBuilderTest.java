package com.seafood.testsupport.builders;

import com.seafood.order.domain.Order;
import com.seafood.order.domain.OrderItem;
import com.seafood.order.domain.OrderStatus;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class OrderBuilderTest {

    private final Instant t0 = Instant.parse("2026-06-01T00:00:00Z");

    @Test
    void defaultBuild_returnsOrderWithDefaults() {
        Order o = OrderBuilder.anOrder().build();
        assertThat(o.id()).isEqualTo("o-test");
        assertThat(o.userId()).isEqualTo("u-test");
        assertThat(o.status()).isInstanceOf(OrderStatus.Pending.class);
        assertThat(o.totalAmount()).isEqualByComparingTo(new BigDecimal("198.00"));
        assertThat(o.items()).hasSize(1);
        assertThat(o.tracking()).isNull();
        assertThat(o.refundId()).isNull();
        assertThat(o.estimatedDelivery()).isNull();
        assertThat(o.createdAt()).isEqualTo(t0);
        assertThat(o.updatedAt()).isEqualTo(t0);
    }

    @Test
    void withId_overridesId() {
        Order o = OrderBuilder.anOrder().withId("o-custom").build();
        assertThat(o.id()).isEqualTo("o-custom");
    }

    @Test
    void withStatus_overridesStatus() {
        Order o = OrderBuilder.anOrder().withStatus(new OrderStatus.Paid()).build();
        assertThat(o.status()).isInstanceOf(OrderStatus.Paid.class);
    }

    @Test
    void withItemsAndTotal_overridesDefaults() {
        OrderItem item = new OrderItem("p-x", "帝王蟹", new BigDecimal("688.00"), 1);
        Order o = OrderBuilder.anOrder()
            .withItems(List.of(item))
            .withTotalAmount(new BigDecimal("688.00"))
            .build();
        assertThat(o.items()).containsExactly(item);
        assertThat(o.totalAmount()).isEqualByComparingTo("688.00");
    }

    @Test
    void multipleBuilds_produceIndependentInstances() {
        OrderBuilder b = OrderBuilder.anOrder();
        Order o1 = b.build();
        Order o2 = b.build();
        assertThat(o1).isNotSameAs(o2);
        assertThat(o1).isEqualTo(o2);
    }

    @Test
    void build_canBeFollowedByRecordNamingMethods() {
        Order o = OrderBuilder.anOrder().build()
            .withEstimatedDelivery(Instant.parse("2026-06-02T00:00:00Z"));
        assertThat(o.estimatedDelivery()).isEqualTo(Instant.parse("2026-06-02T00:00:00Z"));
    }
}