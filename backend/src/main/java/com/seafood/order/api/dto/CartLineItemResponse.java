package com.seafood.order.api.dto;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * 购物车行富化响应(参见 mp-04 购物车 OD 对齐 brief)。
 *
 * <p>相比域对象 {@code CartItem}(只有 productId/quantity/selected/addedAt),这里补上经
 * {@code ProductService} 查到的商品名/单价/图片,不复用 {@link CartItemResponse}
 * ——那是 rebuy 端点专用的窄 DTO,形状不同,复用会搞乱语义。
 *
 * <p>{@code available=false} 对应"商品已下架/被删除"降级场景(购物车里指向失效商品的行是
 * 正常业务场景,不是异常路径):productName 用占位文案,unitPrice 置 0,imageUrl 置空——该行
 * 仍展示(用户可以删除它),只是不可参与结算金额计算。
 *
 * <p>字段命名对齐前端 {@code frontend/pages/cart/cart.js} renderCart() 既有 fallback 链
 * (it.productName / it.unitPrice / it.imageUrl),前端零改动即可吃到新数据。
 */
public record CartLineItemResponse(
        String productId,
        String productName,
        BigDecimal unitPrice,
        String imageUrl,
        int quantity,
        boolean selected,
        Instant addedAt,
        boolean available
) {
}
