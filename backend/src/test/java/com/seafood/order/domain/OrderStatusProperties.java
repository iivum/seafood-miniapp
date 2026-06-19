package com.seafood.order.domain;

import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.Provide;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Sprint 5 C4 — {@code OrderStatus} 状态机 property(tasks §3)。
 *
 * <p>对 7 个状态的全集跑 property:编码 round-trip、终态不可转出、转移自反性。
 */
class OrderStatusProperties {

    @Provide
    Arbitrary<OrderStatus> allStatuses() {
        return Arbitraries.of(
                new OrderStatus.Pending(), new OrderStatus.Paid(), new OrderStatus.Shipped(),
                new OrderStatus.Completed(), new OrderStatus.Cancelled(),
                new OrderStatus.Refunding(), new OrderStatus.Refunded());
    }

    @Provide
    Arbitrary<OrderStatus> terminalStatuses() {
        return Arbitraries.of(new OrderStatus.Cancelled(), new OrderStatus.Refunded());
    }

    /** ∀ 状态:of(code()) 恒等回原状态(API/DB 反序列化 round-trip)。 */
    @Property
    void code_of_roundTripIsIdentity(@ForAll("allStatuses") OrderStatus status) {
        assertThat(OrderStatus.of(status.code())).isEqualTo(status);
    }

    /** ∀ 终态(Cancelled / Refunded)× ∀ 目标:canTransitionTo 恒为 false(终态不可流出)。 */
    @Property
    void terminalStatus_cannotTransitionToAnyTarget(
            @ForAll("terminalStatuses") OrderStatus terminal,
            @ForAll("allStatuses") OrderStatus target) {
        assertThat(terminal.canTransitionTo(target)).isFalse();
    }

    /** ∀ 状态:不可转移到自身(状态机无自环)。 */
    @Property
    void noStatus_canTransitionToItself(@ForAll("allStatuses") OrderStatus status) {
        assertThat(status.canTransitionTo(status)).isFalse();
    }
}
