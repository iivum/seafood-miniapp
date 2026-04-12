package com.seafood.order.application;

import com.seafood.order.domain.model.*;
import com.seafood.order.interfaces.rest.PaymentRequest;
import com.seafood.order.interfaces.rest.RefundRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Order application service
 * Coordinates order operations between domain and infrastructure layers
 * Implements business logic for order management
 */
@Service
@Transactional
public class OrderApplicationService {

    private final OrderRepository orderRepository;
    private final CartApplicationService cartApplicationService;
    private final PaymentService paymentService;
    private final InventorySyncService inventorySyncService;

    public OrderApplicationService(
            OrderRepository orderRepository,
            CartApplicationService cartApplicationService,
            PaymentService paymentService,
            InventorySyncService inventorySyncService) {
        this.orderRepository = orderRepository;
        this.cartApplicationService = cartApplicationService;
        this.paymentService = paymentService;
        this.inventorySyncService = inventorySyncService;
    }

    /**
     * Create order from shopping cart
     *
     * @param userId the user ID
     * @param cartId the cart ID
     * @return the created order
     * @throws IllegalArgumentException if cart is empty or invalid
     */
    public Order createOrderFromCart(String userId, String cartId) {
        validateUserId(userId);

        // Get cart from cart service
        Cart cart = cartApplicationService.getCart(userId);
        if (cart == null || cart.getItems().isEmpty()) {
            throw new IllegalArgumentException("Cart is empty");
        }

        // Create order from cart items
        Address shippingAddress = createDefaultAddress(); // Use default address for now
        Order order = new Order(userId, shippingAddress);

        // Convert cart items to order items
        for (CartItem cartItem : cart.getItems()) {
            OrderItem orderItem = new OrderItem(
                    cartItem.getProductId(),
                    cartItem.getName(),
                    cartItem.getPrice(),
                    cartItem.getQuantity()
            );
            order.addItem(orderItem);
        }

        // Generate order number
        order.generateOrderNumber();

        // Calculate shipping fee
        order.calculateShippingFee();

        // Save order
        Order savedOrder = orderRepository.save(order);

        // Clear cart after successful order creation
        cartApplicationService.clearCart(userId);

        return savedOrder;
    }

    /**
     * Process payment for an order
     *
     * @param orderId the order ID
     * @param paymentRequest payment request details
     * @return the updated order with payment information
     * @throws OrderNotFoundException if order not found
     * @throws IllegalStateException if order status is not PENDING_PAYMENT
     * @throws PaymentException if payment processing fails
     */
    public Order processPayment(String orderId, PaymentRequest paymentRequest) {
        Order order = getOrderOrThrow(orderId);

        // Validate order status
        if (order.getStatus() != OrderStatus.PENDING_PAYMENT) {
            throw new IllegalStateException("Order cannot be paid in current status: " + order.getStatus());
        }

        // Process payment
        String transactionId = paymentService.processPayment(paymentRequest);

        // Update order status
        order.markAsPaid(transactionId);

        // Save updated order
        Order savedOrder = orderRepository.save(order);

        // Synchronize inventory
        inventorySyncService.reduceInventory(order.getItems());

        return savedOrder;
    }

    /**
     * Ship an order
     *
     * @param orderId the order ID
     * @param trackingNumber the tracking number
     * @return the updated order with shipping information
     * @throws OrderNotFoundException if order not found
     * @throws IllegalStateException if order status is not PAID
     */
    public Order shipOrder(String orderId, String trackingNumber) {
        Order order = getOrderOrThrow(orderId);

        // Validate order status
        if (order.getStatus() != OrderStatus.PAID) {
            throw new IllegalStateException("Order must be paid before shipping");
        }

        // Update order status
        order.markAsShipped(trackingNumber);

        // Save updated order
        return orderRepository.save(order);
    }

    /**
     * Complete an order (mark as delivered)
     *
     * @param orderId the order ID
     * @return the updated order
     * @throws OrderNotFoundException if order not found
     * @throws IllegalStateException if order status is not SHIPPED
     */
    public Order completeOrder(String orderId) {
        Order order = getOrderOrThrow(orderId);

        // Validate order status
        if (order.getStatus() != OrderStatus.SHIPPED) {
            throw new IllegalStateException("Order must be shipped before completion");
        }

        // Update order status
        order.markAsDelivered();

        // Save updated order
        return orderRepository.save(order);
    }

    /**
     * Cancel an order
     *
     * @param orderId the order ID
     * @param reason the cancellation reason
     * @return the updated order
     * @throws OrderNotFoundException if order not found
     * @throws IllegalStateException if order status is not PENDING_PAYMENT
     */
    public Order cancelOrder(String orderId, String reason) {
        Order order = getOrderOrThrow(orderId);

        // Validate order status
        if (order.getStatus() != OrderStatus.PENDING_PAYMENT) {
            throw new IllegalStateException("Order cannot be cancelled in current status: " + order.getStatus());
        }

        // Update order status
        order.cancel(reason);

        // Save updated order
        return orderRepository.save(order);
    }

    /**
     * Process refund for an order
     *
     * @param orderId the order ID
     * @param refundRequest refund request details
     * @return the updated order
     * @throws OrderNotFoundException if order not found
     * @throws IllegalStateException if order status is not DELIVERED
     * @throws PaymentException if refund processing fails
     */
    public Order processRefund(String orderId, RefundRequest refundRequest) {
        Order order = getOrderOrThrow(orderId);

        // Validate order status
        if (order.getStatus() != OrderStatus.DELIVERED) {
            throw new IllegalStateException("Order must be delivered before refund");
        }

        // Process refund
        String refundTransactionId = paymentService.processRefund(refundRequest);

        // Update order status
        order.processRefund(refundTransactionId, refundRequest.getReason());

        // Save updated order
        Order savedOrder = orderRepository.save(order);

        // Restore inventory
        inventorySyncService.increaseInventory(order.getItems());

        return savedOrder;
    }

    /**
     * Get orders by user ID and status
     *
     * @param userId the user ID
     * @param status the order status
     * @return list of orders
     */
    public List<Order> getOrdersByUserIdAndStatus(String userId, OrderStatus status) {
        validateUserId(userId);
        return orderRepository.findByUserIdAndStatus(userId, status);
    }

    /**
     * Get order statistics for a user
     *
     * @param userId the user ID
     * @param startDate the start date
     * @param endDate the end date
     * @return order statistics
     */
    public OrderStatistics getOrderStatistics(String userId, LocalDate startDate, LocalDate endDate) {
        validateUserId(userId);
        LocalDateTime start = startDate.atStartOfDay();
        LocalDateTime end = endDate.atTime(23, 59, 59);

        List<Order> orders = orderRepository.findOrdersCreatedBetween(start, end);
        orders = orders.stream()
                .filter(order -> order.getUserId().equals(userId))
                .collect(Collectors.toList());

        return calculateStatistics(orders);
    }

    /**
     * Get order by ID or throw exception
     *
     * @param orderId the order ID
     * @return the order
     * @throws OrderNotFoundException if order not found
     */
    public Order getOrderById(String orderId) {
        return orderRepository.findById(orderId)
                .orElseThrow(() -> new OrderNotFoundException("Order not found: " + orderId));
    }

    /**
     * Get order by ID or throw exception (internal use)
     *
     * @param orderId the order ID
     * @return the order
     * @throws OrderNotFoundException if order not found
     */
    private Order getOrderOrThrow(String orderId) {
        return orderRepository.findById(orderId)
                .orElseThrow(() -> new OrderNotFoundException("Order not found: " + orderId));
    }

    /**
     * Create a default address for order creation
     *
     * @return default address
     */
    private Address createDefaultAddress() {
        return new Address("Default User", "13800138000", "123 Main Street", "Beijing", "Beijing", "Chaoyang");
    }

    /**
     * Validate user ID
     *
     * @param userId the user ID
     * @throws IllegalArgumentException if user ID is null or empty
     */
    private void validateUserId(String userId) {
        if (userId == null || userId.trim().isEmpty()) {
            throw new IllegalArgumentException("User ID cannot be null or empty");
        }
    }

    /**
     * Calculate order statistics from order list
     *
     * @param orders the list of orders
     * @return order statistics
     */
    private OrderStatistics calculateStatistics(List<Order> orders) {
        long totalOrders = orders.size();
        double totalAmount = orders.stream().mapToDouble(Order::getTotalPrice).sum();
        double averageOrderValue = totalOrders > 0 ? totalAmount / totalOrders : 0.0;

        long pendingCount = orders.stream().filter(o -> o.getStatus() == OrderStatus.PENDING_PAYMENT).count();
        long paidCount = orders.stream().filter(o -> o.getStatus() == OrderStatus.PAID).count();
        long shippedCount = orders.stream().filter(o -> o.getStatus() == OrderStatus.SHIPPED).count();
        long deliveredCount = orders.stream().filter(o -> o.getStatus() == OrderStatus.DELIVERED).count();
        long cancelledCount = orders.stream().filter(o -> o.getStatus() == OrderStatus.CANCELLED).count();
        long refundedCount = orders.stream().filter(o -> o.getStatus() == OrderStatus.REFUNDED).count();

        return new OrderStatistics(totalOrders, totalAmount, averageOrderValue,
                                 pendingCount, paidCount, shippedCount, deliveredCount, cancelledCount, refundedCount);
    }

    /**
     * Order statistics inner class
     */
    public static class OrderStatistics {
        private final long totalOrders;
        private final double totalAmount;
        private final double averageOrderValue;
        private final long pendingCount;
        private final long paidCount;
        private final long shippedCount;
        private final long deliveredCount;
        private final long cancelledCount;
        private final long refundedCount;

        public OrderStatistics(long totalOrders, double totalAmount, double averageOrderValue,
                             long pendingCount, long paidCount, long shippedCount,
                             long deliveredCount, long cancelledCount, long refundedCount) {
            this.totalOrders = totalOrders;
            this.totalAmount = totalAmount;
            this.averageOrderValue = averageOrderValue;
            this.pendingCount = pendingCount;
            this.paidCount = paidCount;
            this.shippedCount = shippedCount;
            this.deliveredCount = deliveredCount;
            this.cancelledCount = cancelledCount;
            this.refundedCount = refundedCount;
        }

        // Getters
        public long getTotalOrders() { return totalOrders; }
        public double getTotalAmount() { return totalAmount; }
        public double getAverageOrderValue() { return averageOrderValue; }
        public long getPendingCount() { return pendingCount; }
        public long getPaidCount() { return paidCount; }
        public long getShippedCount() { return shippedCount; }
        public long getDeliveredCount() { return deliveredCount; }
        public long getCancelledCount() { return cancelledCount; }
        public long getRefundedCount() { return refundedCount; }
    }
}