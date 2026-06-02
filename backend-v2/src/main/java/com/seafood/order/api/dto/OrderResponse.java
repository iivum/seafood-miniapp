package com.seafood.order.api.dto;

import com.seafood.order.domain.OrderItem;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public record OrderResponse(
    String id,
    String userId,
    String status,
    List<OrderItem> items,
    BigDecimal totalAmount,
    String paymentRef,
    String cancelReason,
    Instant createdAt,
    Instant updatedAt
) {}
