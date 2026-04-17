package com.seafood.admin.client;

import lombok.Data;
import java.math.BigDecimal;

@Data
public class OrderItemResponse {
    private String productId;
    private String name;
    private BigDecimal price;
    private int quantity;
    private BigDecimal totalPrice;
}
