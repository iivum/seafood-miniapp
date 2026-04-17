package com.seafood.admin.client;

import lombok.Data;
import java.math.BigDecimal;
import java.util.List;

@Data
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

    // Helper method to convert internal status to admin display status
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

    @Data
    public static class AddressResponse {
        private String city;
        private String district;
        private String address;
        private String postalCode;
        private String receiverName;
        private String phone;
    }
}
