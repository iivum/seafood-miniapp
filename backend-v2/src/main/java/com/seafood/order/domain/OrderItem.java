package com.seafood.order.domain;

import java.math.BigDecimal;

public record OrderItem(
    String productId,
    String productName,
    BigDecimal unitPrice,
    int quantity
) {
    public BigDecimal subtotal() {
        return unitPrice.multiply(BigDecimal.valueOf(quantity));
    }
}
