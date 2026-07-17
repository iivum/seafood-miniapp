package com.seafood.order.api.dto;

import jakarta.validation.Valid;

import java.util.List;

/**
 * mp-backend-contract-gaps Task 2a(design.md Gap 2 / D3):POST /api/orders 请求体。
 *
 * <p>{@code items} 为 {@code null} 或空 list 时,{@code OrderService#create} 回退到现有
 * 购物车路径(不变);非空时按显式 items 直接建单,绕开购物车(不读也不清)。
 *
 * <p>fix-order-amount-contract:新增 {@code shippingMethod}(design.md 决策 1)——
 * 客户端只能选配送方式,绝不能提交金额本身;运费/优惠/{@code totalAmount} 全部由后端
 * 权威计算,本 DTO 里没有、也不会有任何金额字段。
 */
public record CreateOrderRequest(
        @Valid List<CartItemRequest> items,
        String shippingMethod
) {
}
