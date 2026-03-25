package com.seafood.order.application;

import com.seafood.order.domain.model.Cart;
import com.seafood.order.domain.model.CartItem;
import com.seafood.order.domain.model.CartRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Objects;

/**
 * Cart application service
 * Coordinates cart operations between domain and infrastructure layers
 */
@Service
@Transactional
public class CartApplicationService {

    private final CartRepository cartRepository;

    public CartApplicationService(CartRepository cartRepository) {
        this.cartRepository = Objects.requireNonNull(cartRepository, "CartRepository cannot be null");
    }

    /**
     * Get or create cart for a user
     *
     * @param userId the user ID
     * @return the cart (new or existing)
     * @throws IllegalArgumentException if userId is null or empty
     */
    public Cart getOrCreateCart(String userId) {
        validateUserId(userId);

        return cartRepository.findByUserId(userId)
                .orElseGet(() -> {
                    Cart newCart = new Cart(userId);
                    return cartRepository.save(newCart);
                });
    }

    /**
     * Add item to cart
     *
     * @param userId the user ID
     * @param productId the product ID
     * @param quantity the quantity
     * @param name the product name
     * @param price the product price
     * @param imageUrl the product image URL
     * @return the updated cart
     * @throws IllegalArgumentException if any parameter is invalid
     */
    public Cart addItemToCart(String userId, String productId, int quantity, String name, double price, String imageUrl) {
        validateUserId(userId);
        validateProductId(productId);
        validateQuantity(quantity);

        Cart cart = getOrCreateCart(userId);
        CartItem newItem = new CartItem(productId, name, price, quantity, imageUrl);
        cart.addItem(newItem);

        return cartRepository.save(cart);
    }

    /**
     * Update item quantity in cart
     *
     * @param userId the user ID
     * @param productId the product ID
     * @param newQuantity the new quantity
     * @return the updated cart
     * @throws IllegalArgumentException if any parameter is invalid
     */
    public Cart updateItemQuantity(String userId, String productId, int newQuantity) {
        validateUserId(userId);
        validateProductId(productId);
        validateQuantityForUpdate(newQuantity);

        Cart cart = getCartOrThrow(userId);
        cart.updateItemQuantity(productId, newQuantity);

        return cartRepository.save(cart);
    }

    /**
     * Remove item from cart
     *
     * @param userId the user ID
     * @param productId the product ID
     * @return the updated cart
     * @throws IllegalArgumentException if any parameter is invalid
     */
    public Cart removeItemFromCart(String userId, String productId) {
        validateUserId(userId);
        validateProductId(productId);

        Cart cart = getCartOrThrow(userId);
        cart.removeItem(productId);

        return cartRepository.save(cart);
    }

    /**
     * Clear all items from cart
     *
     * @param userId the user ID
     * @return the empty cart
     * @throws IllegalArgumentException if userId is null or empty
     */
    public Cart clearCart(String userId) {
        validateUserId(userId);

        Cart cart = getCartOrThrow(userId);
        cart.clear();

        return cartRepository.save(cart);
    }

    /**
     * Toggle item selection in cart
     *
     * @param userId the user ID
     * @param productId the product ID
     * @return the updated cart
     * @throws IllegalArgumentException if any parameter is invalid
     */
    public Cart toggleItemSelection(String userId, String productId) {
        validateUserId(userId);
        validateProductId(productId);

        Cart cart = getCartOrThrow(userId);
        cart.toggleItemSelection(productId);

        return cartRepository.save(cart);
    }

    /**
     * Select all items in cart
     *
     * @param userId the user ID
     * @return the updated cart
     * @throws IllegalArgumentException if userId is null or empty
     */
    public Cart selectAllItems(String userId) {
        validateUserId(userId);

        Cart cart = getCartOrThrow(userId);
        cart.selectAllItems();

        return cartRepository.save(cart);
    }

    /**
     * Deselect all items in cart
     *
     * @param userId the user ID
     * @return the updated cart
     * @throws IllegalArgumentException if userId is null or empty
     */
    public Cart deselectAllItems(String userId) {
        validateUserId(userId);

        Cart cart = getCartOrThrow(userId);
        cart.deselectAllItems();

        return cartRepository.save(cart);
    }

    /**
     * Get cart total price
     *
     * @param userId the user ID
     * @return the total price
     * @throws IllegalArgumentException if userId is null or empty or cart not found
     */
    public double getCartTotalPrice(String userId) {
        validateUserId(userId);

        Cart cart = getCartOrThrow(userId);
        return cart.getTotalPrice();
    }

    /**
     * Get cart total items count
     *
     * @param userId the user ID
     * @return the total items count
     * @throws IllegalArgumentException if userId is null or empty or cart not found
     */
    public int getCartTotalItems(String userId) {
        validateUserId(userId);

        Cart cart = getCartOrThrow(userId);
        return cart.getTotalItems();
    }

    /**
     * Get cart by user ID
     *
     * @param userId the user ID
     * @return the cart
     * @throws IllegalArgumentException if userId is null or empty or cart not found
     */
    public Cart getCart(String userId) {
        validateUserId(userId);
        return getCartOrThrow(userId);
    }

    private Cart getCartOrThrow(String userId) {
        return cartRepository.findByUserId(userId)
                .orElseThrow(() -> new IllegalArgumentException("Cart not found for user: " + userId));
    }

    private void validateUserId(String userId) {
        if (userId == null || userId.trim().isEmpty()) {
            throw new IllegalArgumentException("User ID cannot be null or empty");
        }
    }

    private void validateProductId(String productId) {
        if (productId == null || productId.trim().isEmpty()) {
            throw new IllegalArgumentException("Product ID cannot be null or empty");
        }
    }

    private void validateQuantity(int quantity) {
        if (quantity <= 0) {
            throw new IllegalArgumentException("Quantity must be greater than zero");
        }
    }

    private void validateQuantityForUpdate(int quantity) {
        if (quantity < 0) {
            throw new IllegalArgumentException("Quantity cannot be negative");
        }
    }
}