package com.seafood.admin.client;

import lombok.Data;
import java.math.BigDecimal;
import java.util.List;

@Data
public class OrderResponse {
    private String id;
    private String userId;
    private List<OrderItemResponse> items;
    private BigDecimal totalPrice;
    private String status;
    private String shippingAddress;
}

@Data
class OrderItemResponse {
    private String productId;
    private String productName;
    private BigDecimal price;
    private int quantity;
}
