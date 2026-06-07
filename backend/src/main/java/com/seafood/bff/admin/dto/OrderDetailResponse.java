package com.seafood.bff.admin.dto;

import com.seafood.order.api.dto.OrderResponse;
import com.seafood.product.api.dto.ProductResponse;
import com.seafood.user.api.dto.UserResponse;

import java.util.List;

/**
 * 订单详情(参见 design.md §5.1 GET /api/admin/orders/{id}/detail)。
 *
 * @param order    原始订单
 * @param customer 下单用户
 * @param items    行项 + 关联商品详情(避免前端再二次请求)
 */
public record OrderDetailResponse(
        OrderResponse order,
        UserResponse customer,
        List<ItemWithProduct> items
) {
    public record ItemWithProduct(
            String productId,
            String productName,
            java.math.BigDecimal unitPrice,
            int quantity,
            ProductResponse product
    ) {
    }
}
