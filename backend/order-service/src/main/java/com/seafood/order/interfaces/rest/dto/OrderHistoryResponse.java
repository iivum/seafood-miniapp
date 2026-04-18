package com.seafood.order.interfaces.rest.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Order history response DTO for API serialization.
 *
 * <p>Represents a single event in the order's audit trail,
 * capturing status changes and important milestones.</p>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OrderHistoryResponse {
    /** History entry identifier */
    private String id;
    /** Associated order identifier */
    private String orderId;
    /** Order status at time of this event */
    private String status;
    /** Human-readable event description */
    private String description;
    /** When the event occurred (yyyy-MM-dd HH:mm:ss) */
    private String timestamp;
}
