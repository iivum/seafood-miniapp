package com.seafood.order.infra;

import com.seafood.order.domain.Refund;
import com.seafood.order.domain.RefundStatus;

/** RefundDocument ↔ Refund 域对象映射(参照 OrderMapper 风格)。 */
public final class RefundMapper {

    private RefundMapper() {}

    public static Refund toDomain(RefundDocument d) {
        if (d == null) return null;
        return new Refund(
                d.getId(),
                d.getOrderId(),
                d.getUserId(),
                d.getAmount(),
                d.getReason(),
                RefundStatus.of(d.getStatus()),
                d.getCreatedAt(),
                d.getUpdatedAt());
    }

    public static RefundDocument toDocument(Refund r) {
        RefundDocument d = new RefundDocument();
        d.setId(r.id());
        d.setOrderId(r.orderId());
        d.setUserId(r.userId());
        d.setAmount(r.amount());
        d.setReason(r.reason());
        d.setStatus(r.status().code());
        d.setCreatedAt(r.createdAt());
        d.setUpdatedAt(r.updatedAt());
        return d;
    }
}
