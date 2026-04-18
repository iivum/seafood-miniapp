package com.seafood.order.interfaces.rest.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * Order item response DTO for API serialization.
 *
 * <p>Represents a single line item snapshot at the time of purchase.
 * Price reflects the unit price when the order was placed.</p>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OrderItemResponse {
    /** Product catalog identifier */
    private String productId;
    /** Product name at time of order */
    private String name;
    /** Unit price at time of order (CNY) */
    private BigDecimal price;
    /** Number of units ordered */
    private int quantity;
    /** Computed total (price * quantity) */
    private BigDecimal totalPrice;
}
