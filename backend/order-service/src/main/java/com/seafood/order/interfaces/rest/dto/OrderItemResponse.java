package com.seafood.order.interfaces.rest.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OrderItemResponse {
    private String productId;
    private String name;
    private BigDecimal price;
    private int quantity;
    private BigDecimal totalPrice;
}
