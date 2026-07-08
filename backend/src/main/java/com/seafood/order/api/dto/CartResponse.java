package com.seafood.order.api.dto;

import java.time.Instant;
import java.util.List;

/**
 * 购物车响应(参见 mp-04 购物车 OD 对齐 brief)。
 *
 * <p>{@code items} 是经 {@code CartService} 用 {@code ProductService} 富化过的
 * {@link CartLineItemResponse} 列表,不是域对象 {@code CartItem} 的裸列表——DTO 不反过来
 * 依赖 Service,组装职责在 {@code CartService},这里只是纯数据载体。
 */
public record CartResponse(
        String userId,
        List<CartLineItemResponse> items,
        Instant updatedAt
) {
}
