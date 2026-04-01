package com.seafood.order.interfaces.rest;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

/**
 * Request DTO for payment processing
 * Validates input data for payment operations
 */
public class PaymentRequest {

    @NotBlank(message = "Payment method cannot be blank")
    @NotNull(message = "Payment method cannot be null")
    private String paymentMethod; // wechat, alipay, etc.

    @NotBlank(message = "Amount cannot be blank")
    @NotNull(message = "Amount cannot be null")
    @Positive(message = "Amount must be positive")
    private String amount;

    @NotBlank(message = "User ID cannot be blank")
    @NotNull(message = "User ID cannot be null")
    private String userId;

    @NotBlank(message = "Order ID cannot be blank")
    @NotNull(message = "Order ID cannot be null")
    private String orderId;

    public PaymentRequest() {
    }

    public PaymentRequest(String paymentMethod, String amount, String userId, String orderId) {
        this.paymentMethod = paymentMethod;
        this.amount = amount;
        this.userId = userId;
        this.orderId = orderId;
    }

    // Getters and Setters
    public String getPaymentMethod() {
        return paymentMethod;
    }

    public void setPaymentMethod(String paymentMethod) {
        this.paymentMethod = paymentMethod;
    }

    public String getAmount() {
        return amount;
    }

    public void setAmount(String amount) {
        this.amount = amount;
    }

    public String getUserId() {
        return userId;
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }

    public String getOrderId() {
        return orderId;
    }

    public void setOrderId(String orderId) {
        this.orderId = orderId;
    }

    @Override
    public String toString() {
        return "PaymentRequest{" +
                "paymentMethod='" + paymentMethod + '\'' +
                ", amount='" + amount + '\'' +
                ", userId='" + userId + '\'' +
                ", orderId='" + orderId + '\'' +
                '}';
    }
}