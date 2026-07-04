package com.seafood.order.api.dto;

import jakarta.validation.constraints.Positive;

/**
 * {@code PUT /api/cart/items/{productId}} 请求体 — 只带 {@code quantity}(design D2:
 * 整数替换该行数量,不与 {@code productId} 一起传,productId 已在路径中)。
 */
public record CartQuantityUpdateRequest(
        @Positive int quantity
) {
}
