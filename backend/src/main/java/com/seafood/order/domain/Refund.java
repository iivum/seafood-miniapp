package com.seafood.order.domain;

import com.seafood.shared.error.DomainException;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * 退款单聚合根(参见 design.md §6.3 + specs/admin-batch-operations §Refund lifecycle)。
 *
 * <p>职责:
 * <ul>
 *   <li>持有 {@code orderId} 关联到 {@link Order};</li>
 *   <li>{@code amount} 由退款方在申请时声明,后续 admin 审核可能调整(本期 4.6 不实现部分退款,
 *       Sprint 3 4.8 仍可整体同意 / 拒绝);</li>
 *   <li>{@code reason} 是用户填的退款原因(海鲜质量 / 发货错误 等),长度 ≤ 200 字符;</li>
 *   <li>状态机:REQUESTED → APPROVED / REJECTED(都是终态)。</li>
 * </ul>
 *
 * <p>约束:
 * <ul>
 *   <li>{@code amount > 0} — 0 元退款无意义;</li>
 *   <li>{@code orderId} / {@code userId} 非空 — 退款单必须能回溯到原订单 + 申请人;</li>
 *   <li>状态由命名方法推进,集中异常,避免外部 setStatus 漂移。</li>
 * </ul>
 */
public record Refund(
        String id,
        String orderId,
        String userId,
        BigDecimal amount,
        String reason,
        RefundStatus status,
        Instant createdAt,
        Instant updatedAt
) {

    public Refund {
        if (orderId == null || orderId.isBlank()) {
            throw new DomainException("退款单必须关联原订单");
        }
        if (userId == null || userId.isBlank()) {
            throw new DomainException("退款单必须记录申请人");
        }
        if (amount == null || amount.signum() <= 0) {
            throw new DomainException("退款金额必须大于 0");
        }
        if (reason == null) {
            throw new DomainException("退款原因不能为空");
        }
        if (reason.length() > 200) {
            throw new DomainException("退款原因超过 200 字符上限");
        }
        if (status == null) {
            throw new DomainException("退款状态不能为空");
        }
    }

    /** admin 同意退款:REQUESTED → APPROVED。 */
    public Refund approve(Instant when) {
        if (!(status instanceof RefundStatus.Requested)) {
            throw new DomainException("仅 REQUESTED 退款可同意,当前:" + status.code());
        }
        return mutate(new RefundStatus.Approved(), when);
    }

    /** admin 拒绝退款:REQUESTED → REJECTED。 */
    public Refund reject(Instant when) {
        if (!(status instanceof RefundStatus.Requested)) {
            throw new DomainException("仅 REQUESTED 退款可拒绝,当前:" + status.code());
        }
        return mutate(new RefundStatus.Rejected(), when);
    }

    private Refund mutate(RefundStatus newStatus, Instant when) {
        return new Refund(id, orderId, userId, amount, reason, newStatus,
                createdAt, when == null ? Instant.now() : when);
    }
}
