package com.seafood.user.api.dto;

import java.math.BigDecimal;

/**
 * 收藏列表富化响应(收藏 + 浏览足迹,design.md D3)。
 *
 * <p>{@code available=false} 对应商品已下架/被删除的降级场景,同
 * {@code CartLineItemResponse} 的既有先例:productName 用占位文案,price 置 0,
 * imageUrl 置空——该行仍展示(用户可以取消收藏),只是不可跳转到真实商品详情。
 */
public record FavoriteItemResponse(
        String productId,
        String productName,
        BigDecimal price,
        String imageUrl,
        boolean available
) {
}
