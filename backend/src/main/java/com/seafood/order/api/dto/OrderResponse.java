package com.seafood.order.api.dto;

import com.seafood.order.domain.Order;
import com.seafood.order.domain.OrderItem;
import com.seafood.order.domain.OrderStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public record OrderResponse(
        String id,
        String userId,
        List<OrderItem> items,
        BigDecimal totalAmount,
        String status,
        String cancelReason,
        Instant createdAt,
        Instant updatedAt
) {
    public static OrderResponse from(Order o) {
        return new OrderResponse(
                o.id(), o.userId(), o.items(), o.totalAmount(),
                o.status().code(), o.cancelReason(),
                o.createdAt(), o.updatedAt());
    }
}
