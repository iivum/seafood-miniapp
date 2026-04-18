package com.seafood.order.interfaces.rest.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;

/**
 * Order response DTO for API serialization.
 *
 * <p>Contains the complete order information including items, pricing breakdown,
 * current status, shipping details, and audit history. Dates are formatted
 * as ISO strings for client compatibility.</p>
 *
 * @see OrderItemResponse
 * @see AddressResponse
 * @see OrderHistoryResponse
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OrderResponse {
    /** Unique order identifier */
    private String id;
    /** User who placed the order */
    private String userId;
    /** Human-readable order number for display */
    private String orderNumber;
    /** Line items in the order */
    private List<OrderItemResponse> items;
    /** Sum of item prices before discounts and shipping */
    private BigDecimal totalPrice;
    /** Shipping cost */
    private BigDecimal shippingFee;
    /** Discount amount applied */
    private BigDecimal discountAmount;
    /** Final amount after discounts and shipping */
    private BigDecimal finalPrice;
    /** Current order status */
    private String status;
    /** Order creation timestamp */
    private String createdAt;
    /** Payment confirmation timestamp */
    private String paidAt;
    /** Shipment timestamp */
    private String shippedAt;
    /** Delivery confirmation timestamp */
    private String deliveredAt;
    /** Shipping destination */
    private AddressResponse shippingAddress;
    /** Logistics tracking number */
    private String trackingNumber;
    /** Shipping carrier display name */
    private String carrierName;
    /** Shipping carrier code */
    private String carrierCode;
    /** Payment transaction ID */
    private String transactionId;
    /** Optional customer note */
    private String note;
    /** Complete status change history */
    private List<OrderHistoryResponse> orderHistory;

    /**
     * Get a display-friendly status label for UI rendering.
     *
     * @return localized status text suitable for display
     */
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
