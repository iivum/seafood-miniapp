package com.seafood.order.domain;

import com.seafood.shared.error.DomainException;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/**
 * Order 聚合根(参见 design.md §6.3 + §6.1 orders schema)。
 *
 * <p>状态变更走命名方法,集中规则;外部只读状态用 {@code status()}。
 */
public record Order(
        String id,
        String userId,
        List<OrderItem> items,
        BigDecimal totalAmount,
        OrderStatus status,
        String cancelReason,
        Instant createdAt,
        Instant updatedAt
) {

    public Order {
        if (userId == null || userId.isBlank()) {
            throw new DomainException("userId 不能为空");
        }
        if (items == null || items.isEmpty()) {
            throw new DomainException("订单必须至少包含一行");
        }
        items = List.copyOf(items);
        if (totalAmount == null || totalAmount.signum() <= 0) {
            throw new DomainException("订单金额必须大于 0");
        }
        if (status == null) {
            throw new DomainException("订单状态不能为空");
        }
    }

    /** PENDING → PAID。 */
    public Order markPaid(Instant when) {
        requireTransition(OrderStatus.Paid.class);
        return mutate(new OrderStatus.Paid(), null, when);
    }

    /** PAID → SHIPPED。 */
    public Order markShipped(Instant when) {
        requireTransition(OrderStatus.Shipped.class);
        return mutate(new OrderStatus.Shipped(), null, when);
    }

    /** SHIPPED → COMPLETED。 */
    public Order markCompleted(Instant when) {
        requireTransition(OrderStatus.Completed.class);
        return mutate(new OrderStatus.Completed(), null, when);
    }

    /** 任意可取消状态 → CANCELLED。 */
    public Order cancel(String reason, Instant when) {
        if (!(status instanceof OrderStatus.Pending || status instanceof OrderStatus.Paid)) {
            throw new DomainException("仅 PENDING/PAID 订单可取消,当前:" + status.code());
        }
        return mutate(new OrderStatus.Cancelled(), reason == null ? "" : reason, when);
    }

    private void requireTransition(Class<? extends OrderStatus> target) {
        OrderStatus t = switch (target.getSimpleName()) {
            case "Paid"      -> new OrderStatus.Paid();
            case "Shipped"   -> new OrderStatus.Shipped();
            case "Completed" -> new OrderStatus.Completed();
            default -> throw new IllegalStateException();
        };
        if (!status.canTransitionTo(t)) {
            throw new DomainException("非法状态转移:" + status.code() + " → " + t.code());
        }
    }

    private Order mutate(OrderStatus newStatus, String reason, Instant when) {
        return new Order(id, userId, items, totalAmount, newStatus, reason,
                createdAt, when == null ? Instant.now() : when);
    }
}
