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
