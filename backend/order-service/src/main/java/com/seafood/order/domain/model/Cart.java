package com.seafood.order.domain.model;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Cart aggregate root representing a user's shopping cart
 * Implements domain-driven design principles
 */
public class Cart {

    private String id;
    private String userId;
    private List<CartItem> items;
    private double totalPrice;
    private int totalItems;
    private List<String> selectedItems;

    public Cart(String userId) {
        if (userId == null || userId.trim().isEmpty()) {
            throw new IllegalArgumentException("User ID cannot be null or empty");
        }

        this.id = "cart-" + userId;
        this.userId = userId;
        this.items = new ArrayList<>();
        this.totalPrice = 0.0;
        this.totalItems = 0;
        this.selectedItems = new ArrayList<>();
    }

    public void addItem(CartItem newItem) {
        Objects.requireNonNull(newItem, "Cart item cannot be null");

        // Check if item already exists in cart
        CartItem existingItem = findItemByProductId(newItem.getProductId());

        if (existingItem != null) {
            // Update quantity of existing item
            int newQuantity = existingItem.getQuantity() + newItem.getQuantity();
            existingItem.setQuantity(newQuantity);
        } else {
            // Add new item to cart
            items.add(newItem);
        }

        // Update cart totals
        updateTotals();
    }

    public void updateItemQuantity(String productId, int newQuantity) {
        if (productId == null || productId.trim().isEmpty()) {
            throw new IllegalArgumentException("Product ID cannot be null or empty");
        }

        if (newQuantity < 0) {
            throw new IllegalArgumentException("Quantity cannot be negative");
        }

        CartItem item = findItemByProductId(productId);

        if (item == null) {
            throw new IllegalArgumentException("Item not found in cart: " + productId);
        }

        if (newQuantity == 0) {
            removeItem(productId);
        } else {
            item.setQuantity(newQuantity);
            updateTotals();
        }
    }

    public void removeItem(String productId) {
        if (productId == null || productId.trim().isEmpty()) {
            throw new IllegalArgumentException("Product ID cannot be null or empty");
        }

        CartItem itemToRemove = findItemByProductId(productId);

        if (itemToRemove != null) {
            items.remove(itemToRemove);
            selectedItems.remove(productId);
            updateTotals();
        }
    }

    public void clear() {
        items.clear();
        selectedItems.clear();
        totalPrice = 0.0;
        totalItems = 0;
    }

    public void toggleItemSelection(String productId) {
        if (productId == null || productId.trim().isEmpty()) {
            throw new IllegalArgumentException("Product ID cannot be null or empty");
        }

        if (!findItemIds().contains(productId)) {
            throw new IllegalArgumentException("Item not found in cart: " + productId);
        }

        if (selectedItems.contains(productId)) {
            selectedItems.remove(productId);
        } else {
            selectedItems.add(productId);
        }
    }

    public void selectAllItems() {
        selectedItems.clear();
        for (CartItem item : items) {
            selectedItems.add(item.getProductId());
        }
    }

    public void deselectAllItems() {
        selectedItems.clear();
    }

    private CartItem findItemByProductId(String productId) {
        return items.stream()
                .filter(item -> item.getProductId().equals(productId))
                .findFirst()
                .orElse(null);
    }

    private List<String> findItemIds() {
        List<String> itemIds = new ArrayList<>();
        for (CartItem item : items) {
            itemIds.add(item.getProductId());
        }
        return itemIds;
    }

    private void updateTotals() {
        totalPrice = 0.0;
        totalItems = 0;

        for (CartItem item : items) {
            totalPrice += item.getTotalPrice();
            totalItems += item.getQuantity();
        }
    }

    // Getters
    public String getId() {
        return id;
    }

    public String getUserId() {
        return userId;
    }

    public List<CartItem> getItems() {
        return new ArrayList<>(items);
    }

    public double getTotalPrice() {
        return totalPrice;
    }

    public int getTotalItems() {
        return totalItems;
    }

    public List<String> getSelectedItems() {
        return new ArrayList<>(selectedItems);
    }
}