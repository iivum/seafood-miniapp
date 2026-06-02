package com.seafood.order.api.dto;

import java.time.Instant;
import java.util.List;

public record CartResponse(
    String userId,
    List<CartItemResponse> items,
    Instant updatedAt
) {
    public record CartItemResponse(
        String productId,
        int quantity,
        boolean selected,
        Instant addedAt
    ) {}
}
