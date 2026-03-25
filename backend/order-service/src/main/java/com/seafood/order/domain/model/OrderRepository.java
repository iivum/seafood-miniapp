package com.seafood.order.domain.model;

import java.util.List;
import java.util.Optional;

/**
 * Order repository interface
 * Defines persistence operations for Order aggregate
 * Follows repository pattern to decouple domain from infrastructure
 */
public interface OrderRepository {

    /**
     * Save or update an order
     *
     * @param order the order to save
     * @return the saved order
     */
    Order save(Order order);

    /**
     * Find order by ID
     *
     * @param id the order ID
     * @return optional order if found
     */
    Optional<Order> findById(String id);

    /**
     * Find orders by user ID
     *
     * @param userId the user ID
     * @return list of orders for the user
     */
    List<Order> findByUserId(String userId);

    /**
     * Find order by order number
     *
     * @param orderNumber the order number
     * @return optional order if found
     */
    Optional<Order> findByOrderNumber(String orderNumber);

    /**
     * Find orders by status
     *
     * @param status the order status
     * @return list of orders with the status
     */
    List<Order> findByStatus(OrderStatus status);

    /**
     * Find orders by user ID and status
     *
     * @param userId the user ID
     * @param status the order status
     * @return list of orders for the user with the status
     */
    List<Order> findByUserIdAndStatus(String userId, OrderStatus status);

    /**
     * Delete an order
     *
     * @param order the order to delete
     */
    void delete(Order order);

    /**
     * Delete orders by user ID
     *
     * @param userId the user ID
     */
    void deleteByUserId(String userId);

    /**
     * Count orders by user ID
     *
     * @param userId the user ID
     * @return number of orders for the user
     */
    long countByUserId(String userId);

    /**
     * Count orders by status
     *
     * @param status the order status
     * @return number of orders with the status
     */
    long countByStatus(OrderStatus status);

    /**
     * Find orders created between dates
     *
     * @param startDate the start date
     * @param endDate the end date
     * @return list of orders created in the period
     */
    List<Order> findOrdersCreatedBetween(java.time.LocalDateTime startDate, java.time.LocalDateTime endDate);

    /**
     * Find orders with total price greater than
     *
     * @param minTotalPrice the minimum total price
     * @return list of orders with total price > minTotalPrice
     */
    List<Order> findOrdersWithTotalPriceGreaterThan(double minTotalPrice);

    /**
     * Update order status
     *
     * @param orderId the order ID
     * @param newStatus the new status
     * @return true if updated successfully
     */
    boolean updateOrderStatus(String orderId, OrderStatus newStatus);
}