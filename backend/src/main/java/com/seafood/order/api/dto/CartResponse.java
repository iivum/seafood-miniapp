package com.seafood.order.api.dto;

import com.seafood.order.domain.Cart;
import com.seafood.order.domain.CartItem;

import java.time.Instant;
import java.util.List;

public record CartResponse(
        String userId,
        List<CartItem> items,
        Instant updatedAt
) {
    public static CartResponse from(Cart c) {
        return new CartResponse(c.userId(), c.items(), c.updatedAt());
    }
}
