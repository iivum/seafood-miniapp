package com.seafood.order.interfaces.rest.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OrderResponse {
    private String id;
    private String userId;
    private String orderNumber;
    private List<OrderItemResponse> items;
    private BigDecimal totalPrice;
    private BigDecimal shippingFee;
    private BigDecimal discountAmount;
    private BigDecimal finalPrice;
    private String status;
    private String createdAt;
    private String paidAt;
    private String shippedAt;
    private String deliveredAt;
    private AddressResponse shippingAddress;
    private String trackingNumber;
    private String carrierName;
    private String carrierCode;
    private String transactionId;
    private String note;
    private List<OrderHistoryResponse> orderHistory;

    public String getDisplayStatus() {
        if (status == null) return "PENDING";
        switch (status) {
            case "PENDING_PAYMENT": return "PENDING";
            case "PAID": return "PAID";
            case "SHIPPED": return "SHIPPED";
            case "DELIVERED": return "COMPLETED";
            case "CANCELLED": return "CANCELLED";
            case "REFUNDED": return "REFUNDED";
            default: return status;
        }
    }
}
