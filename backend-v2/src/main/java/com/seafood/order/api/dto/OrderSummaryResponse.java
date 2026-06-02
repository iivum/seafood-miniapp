package com.seafood.order.api.dto;

import java.math.BigDecimal;
import java.time.Instant;

public record OrderSummaryResponse(
    String id,
    String status,
    BigDecimal totalAmount,
    int itemCount,
    Instant createdAt
) {}
