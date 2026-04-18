package com.seafood.order.domain.model;

import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.Date;

/**
 * OrderHistory represents a single event record in the order's audit trail.
 *
 * <p>Every significant state transition and business event is recorded
 * with a timestamp. This provides the complete story of order lifecycle
 * for customer service inquiries and dispute resolution.</p>
 *
 * <p>Examples of recorded events: order created, payment received, order shipped,
 * delivery confirmed, refund initiated, address updated.</p>
 *
 * @see Order
 */
public class OrderHistory {

    /** Human-readable description of the event */
    private String description;
    /** Order status at the time of this event */
    private OrderStatus status;
    /** When the event occurred */
    private Date timestamp;

    public OrderHistory() {
    }

    public OrderHistory(String description, OrderStatus status, Date timestamp) {
        this.description = description;
        this.status = status;
        this.timestamp = timestamp;
    }

    // Getters and Setters
    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public OrderStatus getStatus() {
        return status;
    }

    public void setStatus(OrderStatus status) {
        this.status = status;
    }

    public Date getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(Date timestamp) {
        this.timestamp = timestamp;
    }

    @Override
    public String toString() {
        return "OrderHistory{" +
                "description='" + description + '\'' +
                ", status=" + status +
                ", timestamp=" + timestamp +
                '}';
    }
}