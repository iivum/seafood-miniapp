package com.seafood.order.domain;

import com.seafood.shared.error.DomainException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * OrderStatus 2.6 增量测试:REFUNDING 状态可识别 + 入/出转换规则。
 * <p>完整转换规则:
 * <ul>
 *   <li>4.7 补:COMPLETED → REFUNDING(mp 申请退款入口)</li>
 *   <li>4.8 补:REFUNDING → REFUNDED(admin 同意) / REFUNDING → COMPLETED(admin 拒绝回退)</li>
 * </ul>
 */
class OrderStatusRefundingTest {

    @Test
    void refundingAllowsOutgoingTransitionsPer47And48() {
        // 4.7: REFUNDING 只出到 REFUNDED(admin 同意)或 COMPLETED(admin 拒绝回退)
        OrderStatus r = new OrderStatus.Refunding();
        assertThat(r.canTransitionTo(new OrderStatus.Pending())).isFalse();
        assertThat(r.canTransitionTo(new OrderStatus.Paid())).isFalse();
        assertThat(r.canTransitionTo(new OrderStatus.Shipped())).isFalse();
        assertThat(r.canTransitionTo(new OrderStatus.Cancelled())).isFalse();
        // 4.8 允许的出转换
        assertThat(r.canTransitionTo(new OrderStatus.Refunded())).isTrue();
        assertThat(r.canTransitionTo(new OrderStatus.Completed())).isTrue();
        // self-loop 仍禁止(状态机无 self-edge)
        assertThat(r.canTransitionTo(r)).isFalse();
    }

    @Test
    void completedAllowsTransitionToRefundingPer47() {
        // 4.7 打开 COMPLETED → REFUNDING(mp 申请退款);其它方向仍禁止
        OrderStatus c = new OrderStatus.Completed();
        assertThat(c.canTransitionTo(new OrderStatus.Pending())).isFalse();
        assertThat(c.canTransitionTo(new OrderStatus.Paid())).isFalse();
        assertThat(c.canTransitionTo(new OrderStatus.Shipped())).isFalse();
        assertThat(c.canTransitionTo(new OrderStatus.Cancelled())).isFalse();
        assertThat(c.canTransitionTo(new OrderStatus.Refunding())).isTrue();
    }

    @Test
    void of_refundingStringRoundtrips() {
        assertThat(OrderStatus.of("REFUNDING")).isEqualTo(new OrderStatus.Refunding());
    }

    @Test
    void of_refundingIsCaseSensitiveLowerCaseRejected() {
        // 防止下游 JSON lowercase 串混入
        assertThatThrownBy(() -> OrderStatus.of("refunding"))
                .isInstanceOf(DomainException.class);
    }
}
