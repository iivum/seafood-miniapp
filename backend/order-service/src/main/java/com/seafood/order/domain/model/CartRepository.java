package com.seafood.order.domain.model;

import java.util.List;
import java.util.Optional;

/**
 * Cart repository interface
 * Defines persistence operations for Cart aggregate
 * Follows repository pattern to decouple domain from infrastructure
 */
public interface CartRepository {

    /**
     * Save or update a cart
     *
     * @param cart the cart to save
     * @return the saved cart
     */
    Cart save(Cart cart);

    /**
     * Find cart by user ID
     *
     * @param userId the user ID
     * @return optional cart if found
     */
    Optional<Cart> findByUserId(String userId);

    /**
     * Find cart by ID
     *
     * @param id the cart ID
     * @return optional cart if found
     */
    Optional<Cart> findById(String id);

    /**
     * Delete a cart
     *
     * @param cart the cart to delete
     */
    void delete(Cart cart);

    /**
     * Delete all carts
     */
    void deleteAll();

    /**
     * Delete cart by user ID
     *
     * @param userId the user ID
     */
    void deleteByUserId(String userId);

    /**
     * Count all carts
     *
     * @return total number of carts
     */
    long count();

    /**
     * Find all carts
     *
     * @return list of all carts
     */
    List<Cart> findAll();

    /**
     * Find carts by user IDs
     *
     * @param userIds list of user IDs
     * @return list of carts for the users
     */
    List<Cart> findByUserIdIn(List<String> userIds);

    /**
     * Find carts that contain a specific product
     *
     * @param productId the product ID
     * @return list of carts containing the product
     */
    List<Cart> findCartsWithItem(String productId);

    /**
     * Find carts where a specific product is selected
     *
     * @param productId the product ID
     * @return list of carts with the product selected
     */
    List<Cart> findCartsBySelectedProduct(String productId);

    /**
     * Find carts created before a specific date/time
     *
     * @param dateTime the date/time threshold
     * @return list of carts created before the threshold
     */
    List<Cart> findCartsCreatedBefore(java.time.LocalDateTime dateTime);

    /**
     * Count carts by user ID
     *
     * @param userId the user ID
     * @return number of carts for the user
     */
    long countByUserId(String userId);
}