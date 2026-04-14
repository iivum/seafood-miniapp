package com.seafood.order.domain.model;

import lombok.Getter;
import lombok.Setter;
import org.springframework.data.mongodb.core.mapping.Document;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.UUID;

@Document(collection = "orders")
@Getter
@Setter
public class Order {

    private String id;
    private String userId;
    private String orderNumber;
    private List<OrderItem> items;
    private double totalPrice;
    private double finalPrice;
    private double discountAmount;
    private double shippingFee;
    private OrderStatus status;
    private Address shippingAddress;
    private String transactionId;
    private String trackingNumber;
    private String note;
    private String cancellationReason;
    private String refundTransactionId;
    private String refundReason;
    private Date createdAt;
    private Date paidAt;
    private Date shippedAt;
    private Date deliveredAt;
    private Date cancelledAt;
    private Date refundedAt;
    private List<OrderHistory> orderHistory;

    public Order() {
        // Default constructor for MongoDB/JPA
    }

    public Order(String userId, Address shippingAddress) {
        this.id = UUID.randomUUID().toString();
        this.userId = userId;
        this.shippingAddress = shippingAddress;
        this.status = OrderStatus.PENDING_PAYMENT;
        this.items = new ArrayList<>();
        this.orderHistory = new ArrayList<>();
        this.totalPrice = 0.0;
        this.finalPrice = 0.0;
        this.discountAmount = 0.0;
        this.shippingFee = 0.0;
        this.createdAt = new Date();
        addToHistory("Order created", OrderStatus.PENDING_PAYMENT);
    }

    public void addItem(OrderItem item) {
        this.items.add(item);
        calculateTotalPrice();
    }

    public void generateOrderNumber() {
        if (this.orderNumber == null) {
            this.orderNumber = "SF" + System.currentTimeMillis() + UUID.randomUUID().toString().substring(0, 6).toUpperCase();
        }
    }

    public void markAsPaid(String transactionId) {
        if (this.status != OrderStatus.PENDING_PAYMENT) {
            throw new IllegalStateException("Order cannot be paid in current status: " + this.status);
        }
        this.status = OrderStatus.PAID;
        this.transactionId = transactionId;
        this.paidAt = new Date();
        this.finalPrice = this.totalPrice + this.shippingFee - this.discountAmount;
        addToHistory("Order paid", OrderStatus.PAID);
    }

    public void markAsShipped(String trackingNumber) {
        if (this.status != OrderStatus.PAID) {
            throw new IllegalStateException("Order must be paid before shipping");
        }
        this.status = OrderStatus.SHIPPED;
        this.trackingNumber = trackingNumber;
        this.shippedAt = new Date();
        addToHistory("Order shipped", OrderStatus.SHIPPED);
    }

    public void markAsDelivered() {
        if (this.status != OrderStatus.SHIPPED) {
            throw new IllegalStateException("Order must be shipped before completion");
        }
        this.status = OrderStatus.DELIVERED;
        this.deliveredAt = new Date();
        addToHistory("Order delivered", OrderStatus.DELIVERED);
    }

    public void cancel(String reason) {
        if (this.status != OrderStatus.PENDING_PAYMENT) {
            throw new IllegalStateException("Order cannot be cancelled in current status: " + this.status);
        }
        this.status = OrderStatus.CANCELLED;
        this.cancellationReason = reason;
        this.cancelledAt = new Date();
        addToHistory("Order cancelled: " + reason, OrderStatus.CANCELLED);
    }

    public void processRefund(String refundTransactionId, String reason) {
        if (this.status != OrderStatus.DELIVERED) {
            throw new IllegalStateException("Order must be delivered before refund");
        }
        this.status = OrderStatus.REFUNDED;
        this.refundTransactionId = refundTransactionId;
        this.refundReason = reason;
        this.refundedAt = new Date();
        addToHistory("Order refunded: " + reason, OrderStatus.REFUNDED);
    }

    public void applyDiscount(double discountRate) {
        if (discountRate < 0 || discountRate > 1) {
            throw new IllegalArgumentException("Discount rate must be between 0 and 1");
        }
        this.discountAmount = this.totalPrice * discountRate;
        this.finalPrice = this.totalPrice + this.shippingFee - this.discountAmount;
    }

    public void calculateShippingFee() {
        // Simple shipping fee calculation: free for orders over 100, otherwise 10
        this.shippingFee = this.totalPrice >= 100 ? 0 : 10;
        this.finalPrice = this.totalPrice + this.shippingFee - this.discountAmount;
    }

    public void updateNote(String note) {
        this.note = note;
    }

    public void updateShippingAddress(Address newAddress) {
        this.shippingAddress = newAddress;
        addToHistory("Shipping address updated", this.status);
    }

    public boolean validate() {
        return this.orderNumber != null && !this.items.isEmpty();
    }

    private void calculateTotalPrice() {
        this.totalPrice = this.items.stream()
                .mapToDouble(OrderItem::getTotalPrice)
                .sum();
    }

    private void addToHistory(String description, OrderStatus status) {
        this.orderHistory.add(new OrderHistory(description, status, new Date()));
    }

    // Getters
    public String getId() { return id; }
    public String getUserId() { return userId; }
    public String getOrderNumber() { return orderNumber; }
    public List<OrderItem> getItems() { return new ArrayList<>(items); }
    public double getTotalPrice() { return totalPrice; }
    public double getFinalPrice() { return finalPrice; }
    public double getDiscountAmount() { return discountAmount; }
    public double getShippingFee() { return shippingFee; }
    public OrderStatus getStatus() { return status; }
    public Address getShippingAddress() { return shippingAddress; }
    public String getTransactionId() { return transactionId; }
    public String getTrackingNumber() { return trackingNumber; }
    public String getNote() { return note; }
    public String getCancellationReason() { return cancellationReason; }
    public String getRefundTransactionId() { return refundTransactionId; }
    public String getRefundReason() { return refundReason; }
    public Date getCreatedAt() { return createdAt; }
    public Date getPaidAt() { return paidAt; }
    public Date getShippedAt() { return shippedAt; }
    public Date getDeliveredAt() { return deliveredAt; }
    public Date getCancelledAt() { return cancelledAt; }
    public Date getRefundedAt() { return refundedAt; }
    public List<OrderHistory> getOrderHistory() { return new ArrayList<>(orderHistory); }
}
