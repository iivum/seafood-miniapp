package com.seafood.order.domain.model;

/**
 * Exception thrown when an illegal order status transition is attempted
 */
public class IllegalOrderStatusException extends RuntimeException {
    
    public IllegalOrderStatusException(String message) {
        super(message);
    }
    
    public IllegalOrderStatusException(String message, Throwable cause) {
        super(message, cause);
    }
}
