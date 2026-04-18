package com.seafood.order.domain.model;

/**
 * OrderStatus enum representing the lifecycle states of an order.
 *
 * <p>Valid transitions:</p>
 * <ul>
 *   <li>PENDING_PAYMENT -> PAID (after successful payment)</li>
 *   <li>PENDING_PAYMENT -> CANCELLED (if user cancels or payment times out)</li>
 *   <li>PAID -> SHIPPED (after merchant ships the order)</li>
 *   <li>SHIPPED -> DELIVERED (after delivery confirmation)</li>
 *   <li>DELIVERED -> REFUNDED (after refund processing)</li>
 * </ul>
 *
 * @see Order
 */
public enum OrderStatus {
    /** Order created, awaiting payment from customer */
    PENDING_PAYMENT,
    /** Payment confirmed, order is being processed */
    PAID,
    /** Order shipped, in transit to customer */
    SHIPPED,
    /** Order delivered and confirmed received */
    DELIVERED,
    /** Order cancelled before shipment (payment not yet captured) */
    CANCELLED,
    /** Refund processed after successful delivery */
    REFUNDED
}
