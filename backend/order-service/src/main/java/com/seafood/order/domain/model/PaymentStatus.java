package com.seafood.order.domain.model;

/**
 * PaymentStatus enum representing the states of payment processing.
 *
 * <p>Used by the payment service to track the outcome of payment
 * transactions with external payment gateways (WeChat Pay, Alipay, etc.)</p>
 *
 * @see PaymentService
 */
public enum PaymentStatus {
    /** Payment initiated but not yet completed */
    PENDING,
    /** Payment successfully processed */
    SUCCESS,
    /** Payment failed (declined, insufficient funds, etc.) */
    FAILED,
    /** Payment amount refunded to customer */
    REFUNDED
}