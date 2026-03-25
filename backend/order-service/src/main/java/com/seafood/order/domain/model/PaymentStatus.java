package com.seafood.order.domain.model;

/**
 * Payment status enumeration
 */
public enum PaymentStatus {
    PENDING,      // Payment pending
    SUCCESS,      // Payment successful
    FAILED,       // Payment failed
    REFUNDED      // Payment refunded
}