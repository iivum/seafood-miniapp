package com.seafood.order.domain.model;

/**
 * Exception thrown when an order lookup fails.
 *
 * <p>This typically occurs when:</p>
 * <ul>
 *   <li>The order ID does not exist in the database</li>
 *   <li>The order belongs to a different user (security consideration)</li>
 *   <li>The order was deleted after creation</li>
 * </ul>
 *
 * @see OrderRepository
 */
public class OrderNotFoundException extends RuntimeException {

    public OrderNotFoundException(String message) {
        super(message);
    }
}