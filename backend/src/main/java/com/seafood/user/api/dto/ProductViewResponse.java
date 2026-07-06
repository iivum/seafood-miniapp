package com.seafood.user.api.dto;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * 浏览足迹列表富化响应(收藏 + 浏览足迹,design.md D3)。字段/降级语义同
 * {@link FavoriteItemResponse},多一个 {@code viewedAt} 用于按时间倒序展示。
 */
public record ProductViewResponse(
        String productId,
        String productName,
        BigDecimal price,
        String imageUrl,
        boolean available,
        Instant viewedAt
) {
}
