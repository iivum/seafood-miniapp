package com.seafood.order.domain;

import com.seafood.shared.error.DomainException;

import java.math.BigDecimal;

/**
 * 订单行项 — 价格/数量快照(参见 design.md §6.1 orders.items[])。
 * 下单瞬间复制商品当时的价格,后续商品涨价/促销不影响订单金额。
 */
public record OrderItem(
        String productId,
        String productName,
        BigDecimal unitPrice,
        int quantity
) {
    public OrderItem {
        if (productId == null || productId.isBlank()) {
            throw new IllegalArgumentException("productId 不能为空");
        }
        if (productName == null || productName.isBlank()) {
            throw new IllegalArgumentException("productName 不能为空");
        }
        if (unitPrice == null || unitPrice.signum() <= 0) {
            throw new DomainException("单价必须大于 0");
        }
        if (quantity <= 0) {
            throw new DomainException("数量必须大于 0");
        }
    }

    public BigDecimal subtotal() {
        return unitPrice.multiply(BigDecimal.valueOf(quantity));
    }
}
