package com.seafood.order.interfaces.rest;

import com.seafood.order.application.OrderApplicationService;
import com.seafood.order.domain.model.Order;
import com.seafood.order.domain.model.OrderStatus;
import com.seafood.order.interfaces.rest.dto.OrderResponse;
import com.seafood.order.interfaces.rest.mapper.OrderMapper;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import jakarta.validation.Valid;
import java.time.LocalDate;
import java.util.List;

/**
 * Order REST controller providing endpoints for order lifecycle management.
 *
 * <p>Handles order creation, status transitions, payment processing,
 * shipping, and refunds. All endpoints return standardized JSON responses.</p>
 *
 * @see OrderApplicationService
 * @see OrderResponse
 */
@RestController
@RequestMapping("/orders")
public class OrderController {

    private final OrderApplicationService orderApplicationService;
    private final OrderMapper orderMapper;

    public OrderController(OrderApplicationService orderApplicationService, OrderMapper orderMapper) {
        this.orderApplicationService = orderApplicationService;
        this.orderMapper = orderMapper;
    }

    /**
     * Create a new order from the user's shopping cart.
     *
     * <p>Items are copied from cart to order with price snapshots.
     * Cart is cleared after successful order creation.</p>
     *
     * @param request contains userId, cartId, addressId, and optional selectedItemIds
     * @return created order with PENDING_PAYMENT status
     */
    @PostMapping
    public ResponseEntity<OrderResponse> createOrderFromCart(@Valid @RequestBody CreateOrderRequest request) {
        Order createdOrder = orderApplicationService.createOrderFromCart(request.getUserId(), request.getCartId());
        return new ResponseEntity<>(orderMapper.toResponse(createdOrder), HttpStatus.CREATED);
    }

    /**
     * Retrieve a specific order by its ID.
     *
     * @param orderId the unique order identifier
     * @return order details if found
     */
    @GetMapping("/{id}")
    public ResponseEntity<OrderResponse> getOrderById(@PathVariable("id") String orderId) {
        Order order = orderApplicationService.getOrderById(orderId);
        return ResponseEntity.ok(orderMapper.toResponse(order));
    }

    /**
     * List orders with optional filtering by user and/or status.
     *
     * <p>When called by admin without parameters, returns all orders.
     * Parameters can be combined for filtered queries.</p>
     *
     * @param userId filter by user (optional)
     * @param status filter by order status (optional)
     * @param sortBy field to sort by, default: createdAt
     * @param sortDir sort direction, default: desc
     * @return list of matching orders
     */
    @GetMapping
    public ResponseEntity<List<OrderResponse>> getOrdersByUserAndStatus(
            @RequestParam(value = "userId", required = false) String userId,
            @RequestParam(value = "status", required = false) OrderStatus status,
            @RequestParam(value = "sortBy", defaultValue = "createdAt") String sortBy,
            @RequestParam(value = "sortDir", defaultValue = "desc") String sortDir) {
        List<Order> orders;
        if (userId != null && status != null) {
            orders = orderApplicationService.getOrdersByUserIdAndStatus(userId, status, sortBy, sortDir);
        } else if (userId != null) {
            orders = orderApplicationService.getOrdersByUserId(userId, sortBy, sortDir);
        } else if (status != null) {
            orders = orderApplicationService.getOrdersByStatus(status, sortBy, sortDir);
        } else {
            orders = orderApplicationService.getAllOrders(sortBy, sortDir);
        }
        return ResponseEntity.ok(orders.stream().map(orderMapper::toResponse).toList());
    }

    /**
     * Get all orders (for admin)
     *
     * @param sortBy sort field (optional, default: createdAt)
     * @param sortDir sort direction (optional, default: desc)
     * @return list of all orders
     */
    @GetMapping("/all")
    public ResponseEntity<List<OrderResponse>> getAllOrders(
            @RequestParam(value = "sortBy", defaultValue = "createdAt") String sortBy,
            @RequestParam(value = "sortDir", defaultValue = "desc") String sortDir) {
        return ResponseEntity.ok(orderApplicationService.getAllOrders(sortBy, sortDir).stream().map(orderMapper::toResponse).toList());
    }

    /**
     * Process payment for an order
     *
     * @param orderId the order ID
     * @param paymentRequest the payment request details
     * @return the updated order
     */
    @PostMapping("/{id}/payment")
    public ResponseEntity<OrderResponse> processPayment(
            @PathVariable("id") String orderId,
            @Valid @RequestBody PaymentRequest paymentRequest) {
        Order updatedOrder = orderApplicationService.processPayment(orderId, paymentRequest);
        return ResponseEntity.ok(orderMapper.toResponse(updatedOrder));
    }

    /**
     * Ship an order
     *
     * @param orderId the order ID
     * @param trackingNumber the tracking number
     * @return the updated order
     */
    @PutMapping("/{id}/ship")
    public ResponseEntity<OrderResponse> shipOrder(
            @PathVariable("id") String orderId,
            @RequestParam("trackingNumber") String trackingNumber) {
        Order updatedOrder = orderApplicationService.shipOrder(orderId, trackingNumber);
        return ResponseEntity.ok(orderMapper.toResponse(updatedOrder));
    }

    /**
     * Complete an order (mark as delivered)
     *
     * @param orderId the order ID
     * @return the updated order
     */
    @PutMapping("/{id}/complete")
    public ResponseEntity<OrderResponse> completeOrder(@PathVariable("id") String orderId) {
        Order updatedOrder = orderApplicationService.completeOrder(orderId);
        return ResponseEntity.ok(orderMapper.toResponse(updatedOrder));
    }

    /**
     * Cancel an order
     *
     * @param orderId the order ID
     * @param reason the cancellation reason
     * @return the updated order
     */
    @PutMapping("/{id}/cancel")
    public ResponseEntity<OrderResponse> cancelOrder(
            @PathVariable("id") String orderId,
            @RequestParam("reason") String reason) {
        Order updatedOrder = orderApplicationService.cancelOrder(orderId, reason);
        return ResponseEntity.ok(orderMapper.toResponse(updatedOrder));
    }

    /**
     * Process refund for an order
     *
     * @param orderId the order ID
     * @param refundRequest the refund request details
     * @return the updated order
     */
    @PostMapping("/{id}/refund")
    public ResponseEntity<OrderResponse> processRefund(
            @PathVariable("id") String orderId,
            @Valid @RequestBody RefundRequest refundRequest) {
        Order updatedOrder = orderApplicationService.processRefund(orderId, refundRequest);
        return ResponseEntity.ok(orderMapper.toResponse(updatedOrder));
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