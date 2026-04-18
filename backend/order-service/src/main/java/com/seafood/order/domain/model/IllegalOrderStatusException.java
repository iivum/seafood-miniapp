package com.seafood.order.domain.model;

/**
 * Exception thrown when an invalid order state transition is attempted.
 *
 * <p>Enforces the order state machine by rejecting operations that are
 * not valid for the current order status. For example:</p>
 * <ul>
 *   <li>Attempting to ship an unpaid order</li>
 *   <li>Attempting to cancel a shipped order</li>
 *   <li>Attempting to refund an undelivered order</li>
 * </ul>
 *
 * @see OrderStatus
 * @see Order
 */
public class IllegalOrderStatusException extends RuntimeException {

    public IllegalOrderStatusException(String message) {
        super(message);
    }

    public IllegalOrderStatusException(String message, Throwable cause) {
        super(message, cause);
    }
}
