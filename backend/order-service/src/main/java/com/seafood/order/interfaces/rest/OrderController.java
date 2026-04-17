package com.seafood.order.interfaces.rest;

import com.seafood.order.application.OrderApplicationService;
import com.seafood.order.domain.model.Order;
import com.seafood.order.domain.model.OrderStatus;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import jakarta.validation.Valid;
import java.time.LocalDate;
import java.util.List;

/**
 * Order REST controller
 * Provides API endpoints for order management
 */
@RestController
@RequestMapping("/orders")
public class OrderController {

    private final OrderApplicationService orderApplicationService;

    public OrderController(OrderApplicationService orderApplicationService) {
        this.orderApplicationService = orderApplicationService;
    }

    /**
     * Create order from shopping cart
     *
     * @param request the create order request
     * @return the created order
     */
    @PostMapping
    public ResponseEntity<Order> createOrderFromCart(@Valid @RequestBody CreateOrderRequest request) {
        Order createdOrder = orderApplicationService.createOrderFromCart(request.getUserId(), request.getCartId());
        return new ResponseEntity<>(createdOrder, HttpStatus.CREATED);
    }

    /**
     * Get order by ID
     *
     * @param orderId the order ID
     * @return the order details
     */
    @GetMapping("/{id}")
    public ResponseEntity<Order> getOrderById(@PathVariable("id") String orderId) {
        Order order = orderApplicationService.getOrderById(orderId);
        return ResponseEntity.ok(order);
    }

    /**
     * Get orders by user ID and status (both parameters optional for admin)
     *
     * @param userId the user ID (optional)
     * @param status the order status (optional)
     * @return list of orders
     */
    @GetMapping
    public ResponseEntity<List<Order>> getOrdersByUserAndStatus(
            @RequestParam(value = "userId", required = false) String userId,
            @RequestParam(value = "status", required = false) OrderStatus status) {
        List<Order> orders;
        if (userId != null && status != null) {
            orders = orderApplicationService.getOrdersByUserIdAndStatus(userId, status);
        } else {
            orders = orderApplicationService.getAllOrders();
        }
        return ResponseEntity.ok(orders);
    }

    /**
     * Get all orders (for admin)
     *
     * @return list of all orders
     */
    @GetMapping("/all")
    public ResponseEntity<List<Order>> getAllOrders() {
        return ResponseEntity.ok(orderApplicationService.getAllOrders());
    }

    /**
     * Process payment for an order
     *
     * @param orderId the order ID
     * @param paymentRequest the payment request details
     * @return the updated order
     */
    @PostMapping("/{id}/payment")
    public ResponseEntity<Order> processPayment(
            @PathVariable("id") String orderId,
            @Valid @RequestBody PaymentRequest paymentRequest) {
        Order updatedOrder = orderApplicationService.processPayment(orderId, paymentRequest);
        return ResponseEntity.ok(updatedOrder);
    }

    /**
     * Ship an order
     *
     * @param orderId the order ID
     * @param trackingNumber the tracking number
     * @return the updated order
     */
    @PutMapping("/{id}/ship")
    public ResponseEntity<Order> shipOrder(
            @PathVariable("id") String orderId,
            @RequestParam("trackingNumber") String trackingNumber) {
        Order updatedOrder = orderApplicationService.shipOrder(orderId, trackingNumber);
        return ResponseEntity.ok(updatedOrder);
    }

    /**
     * Complete an order (mark as delivered)
     *
     * @param orderId the order ID
     * @return the updated order
     */
    @PutMapping("/{id}/complete")
    public ResponseEntity<Order> completeOrder(@PathVariable("id") String orderId) {
        Order updatedOrder = orderApplicationService.completeOrder(orderId);
        return ResponseEntity.ok(updatedOrder);
    }

    /**
     * Cancel an order
     *
     * @param orderId the order ID
     * @param reason the cancellation reason
     * @return the updated order
     */
    @PutMapping("/{id}/cancel")
    public ResponseEntity<Order> cancelOrder(
            @PathVariable("id") String orderId,
            @RequestParam("reason") String reason) {
        Order updatedOrder = orderApplicationService.cancelOrder(orderId, reason);
        return ResponseEntity.ok(updatedOrder);
    }

    /**
     * Process refund for an order
     *
     * @param orderId the order ID
     * @param refundRequest the refund request details
     * @return the updated order
     */
    @PostMapping("/{id}/refund")
    public ResponseEntity<Order> processRefund(
            @PathVariable("id") String orderId,
            @Valid @RequestBody RefundRequest refundRequest) {
        Order updatedOrder = orderApplicationService.processRefund(orderId, refundRequest);
        return ResponseEntity.ok(updatedOrder);
    }

    /**
     * Get order statistics for a user
     *
     * @param userId the user ID
     * @param startDate the start date
     * @param endDate the end date
     * @return order statistics
     */
    @GetMapping("/{userId}/statistics")
    public ResponseEntity<OrderApplicationService.OrderStatistics> getOrderStatistics(
            @PathVariable("userId") String userId,
            @RequestParam("startDate") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam("endDate") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {
        OrderApplicationService.OrderStatistics statistics = orderApplicationService.getOrderStatistics(userId, startDate, endDate);
        return ResponseEntity.ok(statistics);
    }
}