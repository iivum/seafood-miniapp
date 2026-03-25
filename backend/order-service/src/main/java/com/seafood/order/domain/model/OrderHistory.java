package com.seafood.order.domain.model;

import java.util.Date;

/**
 * Order history record
 * Tracks order status changes and important events
 */
public class OrderHistory {

    private String description;
    private OrderStatus status;
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