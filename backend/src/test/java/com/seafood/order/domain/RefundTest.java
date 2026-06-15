package com.seafood.order.domain;

import com.seafood.shared.error.DomainException;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 路线图 4.6 退款聚合根状态机 + 不变式测试。
 *
 * <p>覆盖:
 * <ul>
 *   <li>REQUESTED → APPROVED / REJECTED 都行</li>
 *   <li>终态不可再变(APPROVED 不能 approve,REJECTED 不能 reject)</li>
 *   <li>{@code amount <= 0} / {@code reason > 200} / orderId 空 都拒</li>
 *   <li>RefundStatus.of 反序列化 + 未知字符串抛 DomainException</li>
 * </ul>
 */
class RefundTest {

    private Refund requested() {
        return new Refund(
                "r1",
                "o-1",
                "u-1",
                new BigDecimal("99.00"),
                "海鲜质量有问题",
                new RefundStatus.Requested(),
                Instant.now(),
                Instant.now());
    }

    @Test
    void approve_movesRequestedToApproved() {
        Refund approved = requested().approve(Instant.now());
        assertThat(approved.status()).isInstanceOf(RefundStatus.Approved.class);
    }

    @Test
    void reject_movesRequestedToRejected() {
        Refund rejected = requested().reject(Instant.now());
        assertThat(rejected.status()).isInstanceOf(RefundStatus.Rejected.class);
    }

    @Test
    void approved_cannotApproveAgain() {
        Refund approved = requested().approve(Instant.now());
        assertThatThrownBy(() -> approved.approve(Instant.now()))
                .isInstanceOf(DomainException.class)
                .hasMessageContaining("REQUESTED");
    }

    @Test
    void rejected_cannotRejectAgain() {
        Refund rejected = requested().reject(Instant.now());
        assertThatThrownBy(() -> rejected.reject(Instant.now()))
                .isInstanceOf(DomainException.class)
                .hasMessageContaining("REQUESTED");
    }

    @Test
    void amount_mustBePositive() {
        assertThatThrownBy(() -> new Refund("r1", "o-1", "u-1",
                BigDecimal.ZERO, "x", new RefundStatus.Requested(),
                Instant.now(), Instant.now()))
                .isInstanceOf(DomainException.class)
                .hasMessageContaining("退款金额");
    }

    @Test
    void amount_mustNotBeNull() {
        assertThatThrownBy(() -> new Refund("r1", "o-1", "u-1",
                null, "x", new RefundStatus.Requested(),
                Instant.now(), Instant.now()))
                .isInstanceOf(DomainException.class);
    }

    @Test
    void reason_over200CharsRejected() {
        String tooLong = "x".repeat(201);
        assertThatThrownBy(() -> new Refund("r1", "o-1", "u-1",
                new BigDecimal("1"), tooLong, new RefundStatus.Requested(),
                Instant.now(), Instant.now()))
                .isInstanceOf(DomainException.class)
                .hasMessageContaining("200");
    }

    @Test
    void orderId_mustNotBeBlank() {
        assertThatThrownBy(() -> new Refund("r1", "", "u-1",
                new BigDecimal("1"), "x", new RefundStatus.Requested(),
                Instant.now(), Instant.now()))
                .isInstanceOf(DomainException.class)
                .hasMessageContaining("订单");
    }

    @Test
    void userId_mustNotBeBlank() {
        assertThatThrownBy(() -> new Refund("r1", "o-1", "",
                new BigDecimal("1"), "x", new RefundStatus.Requested(),
                Instant.now(), Instant.now()))
                .isInstanceOf(DomainException.class)
                .hasMessageContaining("申请人");
    }

    @Test
    void status_ofRoundtripsAllValues() {
        assertThat(RefundStatus.of("REQUESTED")).isInstanceOf(RefundStatus.Requested.class);
        assertThat(RefundStatus.of("APPROVED")).isInstanceOf(RefundStatus.Approved.class);
        assertThat(RefundStatus.of("REJECTED")).isInstanceOf(RefundStatus.Rejected.class);
    }

    @Test
    void status_ofThrowsOnUnknown() {
        assertThatThrownBy(() -> RefundStatus.of("FOO"))
                .isInstanceOf(DomainException.class)
                .hasMessageContaining("未知");
    }

    @Test
    void status_isTerminalForApprovedAndRejected() {
        assertThat(new RefundStatus.Approved().isTerminal()).isTrue();
        assertThat(new RefundStatus.Rejected().isTerminal()).isTrue();
        assertThat(new RefundStatus.Requested().isTerminal()).isFalse();
    }
}
