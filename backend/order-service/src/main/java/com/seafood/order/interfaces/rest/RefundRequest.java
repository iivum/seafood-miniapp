package com.seafood.order.interfaces.rest;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Positive;

/**
 * Request DTO for refund processing
 * Validates input data for refund operations
 */
public class RefundRequest {

    @NotBlank(message = "Refund amount cannot be blank")
    @NotNull(message = "Refund amount cannot be null")
    @Positive(message = "Refund amount must be positive")
    private String refundAmount;

    @NotBlank(message = "Refund reason cannot be blank")
    @NotNull(message = "Refund reason cannot be null")
    private String reason;

    @NotBlank(message = "User ID cannot be blank")
    @NotNull(message = "User ID cannot be null")
    private String userId;

    @NotBlank(message = "Order ID cannot be blank")
    @NotNull(message = "Order ID cannot be null")
    private String orderId;

    public RefundRequest() {
    }

    public RefundRequest(String refundAmount, String reason, String userId, String orderId) {
        this.refundAmount = refundAmount;
        this.reason = reason;
        this.userId = userId;
        this.orderId = orderId;
    }

    // Getters and Setters
    public String getRefundAmount() {
        return refundAmount;
    }

    public void setRefundAmount(String refundAmount) {
        this.refundAmount = refundAmount;
    }

    public String getReason() {
        return reason;
    }

    public void setReason(String reason) {
        this.reason = reason;
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
        return "RefundRequest{" +
                "refundAmount='" + refundAmount + '\'' +
                ", reason='" + reason + '\'' +
                ", userId='" + userId + '\'' +
                ", orderId='" + orderId + '\'' +
                '}';
    }
}