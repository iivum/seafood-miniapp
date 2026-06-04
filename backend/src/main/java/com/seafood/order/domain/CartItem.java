package com.seafood.order.domain;

import java.time.Instant;

/** 购物车单行(参见 design.md §6.1 carts collection)。 */
public record CartItem(
        String productId,
        int quantity,
        boolean selected,
        Instant addedAt
) {
    public CartItem {
        if (productId == null || productId.isBlank()) {
            throw new IllegalArgumentException("productId 不能为空");
        }
        if (quantity <= 0) {
            throw new IllegalArgumentException("quantity 必须大于 0");
        }
        if (addedAt == null) {
            addedAt = Instant.now();
        }
    }
}
