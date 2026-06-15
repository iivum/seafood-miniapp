package com.seafood.order.api.dto;

import com.seafood.order.domain.Refund;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * 退款单响应(参见 design.md §6.3 + specs/admin-batch-operations §Refund lifecycle)。
 *
 * <p>路线图 4.7 引入。mp 端申请后只回写 own 退款单,ad-06 审核列表会取 {@code status} + reason
 * 渲染;订单是否已转 REFUNDING 由 Order 实体同步维护,前端拿 Order 详情比对。
 */
public record RefundResponse(
        String id,
        String orderId,
        String userId,
        BigDecimal amount,
        String reason,
        String status,
        Instant createdAt,
        Instant updatedAt
) {
    public static RefundResponse from(Refund r) {
        return new RefundResponse(
                r.id(),
                r.orderId(),
                r.userId(),
                r.amount(),
                r.reason(),
                r.status().code(),
                r.createdAt(),
                r.updatedAt());
    }
}
