package com.seafood.order.domain.model;

import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * OrderItem represents a single line item within an order.
 *
 * <p>Each OrderItem captures the product information at the time of purchase,
 * including price and quantity. This snapshot approach ensures order totals
 * remain accurate even if product prices change later.</p>
 *
 * <p>OrderItem is a value object within the Order aggregate - it does not have
 * its own lifecycle and cannot be modified independently of the order.</p>
 *
 * @see Order
 * @see CartItem
 */
@Data
@NoArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "_class")
public class OrderItem {
    /** Product identifier from the product catalog */
    private String productId;
    /** Product name at time of purchase (snapshot) */
    private String name;
    /** Unit price at time of purchase (snapshot, in CNY) */
    private double price;
    /** Number of units ordered */
    private int quantity;

    public OrderItem(String productId, String name, double price, int quantity) {
        this.productId = productId;
        this.name = name;
        this.price = price;
        this.quantity = quantity;
    }

    public double getTotalPrice() {
        return price * quantity;
    }
}
