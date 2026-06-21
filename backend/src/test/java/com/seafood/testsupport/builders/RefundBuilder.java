package com.seafood.testsupport.builders;

import com.seafood.order.domain.Refund;
import com.seafood.order.domain.RefundStatus;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * RefundBuilder — D1 5 builder 之一。
 *
 * <p>核心字段:id / orderId / userId / amount / reason / status / createdAt / updatedAt。
 * 8 个字段都是 record 必填(builder 全部覆蓋,因 Refund 字段少且没有 nullable)。
 */
public final class RefundBuilder {

    private static final Instant DEFAULT_T = Instant.parse("2026-06-01T00:00:00Z");

    private String id = "r-test";
    private String orderId = "o-test";
    private String userId = "u-test";
    private BigDecimal amount = new BigDecimal("99.00");
    private String reason = "不再需要";
    private RefundStatus status = new RefundStatus.Requested();
    private Instant createdAt = DEFAULT_T;
    private Instant updatedAt = DEFAULT_T;

    private RefundBuilder() {}

    public static RefundBuilder aRefund() {
        return new RefundBuilder();
    }

    public RefundBuilder withId(String id) { this.id = id; return this; }
    public RefundBuilder withOrderId(String orderId) { this.orderId = orderId; return this; }
    public RefundBuilder withUserId(String userId) { this.userId = userId; return this; }
    public RefundBuilder withAmount(BigDecimal amount) { this.amount = amount; return this; }
    public RefundBuilder withReason(String reason) { this.reason = reason; return this; }
    public RefundBuilder withStatus(RefundStatus status) { this.status = status; return this; }
    public RefundBuilder withCreatedAt(Instant createdAt) { this.createdAt = createdAt; return this; }
    public RefundBuilder withUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; return this; }

    public Refund build() {
        return new Refund(id, orderId, userId, amount, reason, status, createdAt, updatedAt);
    }
}
