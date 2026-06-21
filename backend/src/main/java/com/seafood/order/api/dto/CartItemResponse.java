package com.seafood.order.api.dto;

import java.time.Instant;

/**
 * sprint-1-closure 1.4 — rebuy 端点返回的 cart 项。前端拿到后用 {@code POST /api/cart/items}
 * 加进购物车(用 productId + quantity 即可,selected 默认真)。
 *
 * <p>形状与 {@code CartItem} 域对象保持一致(productId + quantity + selected + addedAt),
 * 不带 SKU(Sprint 2 SKU 选规格落地后再扩)。
 */
public record CartItemResponse(
        String productId,
        int quantity,
        boolean selected,
        Instant addedAt
) {
}
