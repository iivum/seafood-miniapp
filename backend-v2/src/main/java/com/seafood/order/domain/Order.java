package com.seafood.order.domain;

import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.Version;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

@Document(collection = "orders")
public record Order(
    @Id String id,
    @Indexed String userId,
    List<OrderItem> items,
    BigDecimal totalAmount,
    @Indexed OrderStatus status,
    String paymentRef,
    String cancelReason,
    Instant createdAt,
    Instant updatedAt,
    @Version Long version
) {
    public static Order create(String userId, List<OrderItem> items, Instant now) {
        BigDecimal total = items.stream()
            .map(OrderItem::subtotal)
            .reduce(BigDecimal.ZERO, BigDecimal::add);
        return new Order(null, userId, List.copyOf(items), total,
            OrderStatus.PENDING, null, null, now, now, null);
    }

    public Order markPaid(String paymentRef, Instant when) {
        if (status != OrderStatus.PENDING) {
            throw new IllegalStateException("只有 PENDING 订单可以标记已支付,当前: " + status);
        }
        return new Order(id, userId, items, totalAmount, OrderStatus.PAID,
            paymentRef, cancelReason, createdAt, when, version);
    }

    public Order ship(Instant when) {
        if (status != OrderStatus.PAID) {
            throw new IllegalStateException("只有 PAID 订单可以发货,当前: " + status);
        }
        return new Order(id, userId, items, totalAmount, OrderStatus.SHIPPED,
            paymentRef, cancelReason, createdAt, when, version);
    }

    public Order complete(Instant when) {
        if (status != OrderStatus.SHIPPED) {
            throw new IllegalStateException("只有 SHIPPED 订单可以完成,当前: " + status);
        }
        return new Order(id, userId, items, totalAmount, OrderStatus.COMPLETED,
            paymentRef, cancelReason, createdAt, when, version);
    }

    public Order cancel(String reason, Instant when) {
        if (status == OrderStatus.SHIPPED || status == OrderStatus.COMPLETED) {
            throw new IllegalStateException("已发货/已完成订单不能取消,当前: " + status);
        }
        return new Order(id, userId, items, totalAmount, OrderStatus.CANCELLED,
            paymentRef, reason, createdAt, when, version);
    }
}
