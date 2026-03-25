package com.seafood.order.domain.model;

import java.math.BigDecimal;
import java.util.Objects;

/**
 * CartItem represents an item in the shopping cart
 * Part of the Cart aggregate
 */
public class CartItem {

    private String id;
    private String productId;
    private String name;
    private double price;
    private int quantity;
    private String imageUrl;

    public CartItem(String productId, String name, double price, int quantity, String imageUrl) {
        validateConstructorParams(productId, name, price, quantity);

        this.id = generateId(productId);
        this.productId = productId;
        this.name = name;
        this.price = price;
        this.quantity = quantity;
        this.imageUrl = imageUrl;
    }

    private void validateConstructorParams(String productId, String name, double price, int quantity) {
        if (productId == null || productId.trim().isEmpty()) {
            throw new IllegalArgumentException("Product ID cannot be null or empty");
        }

        if (name == null || name.trim().isEmpty()) {
            throw new IllegalArgumentException("Product name cannot be null or empty");
        }

        if (price < 0) {
            throw new IllegalArgumentException("Price cannot be negative");
        }

        if (quantity <= 0) {
            throw new IllegalArgumentException("Quantity must be greater than zero");
        }
    }

    private String generateId(String productId) {
        return "cart-item-" + productId;
    }

    public String getId() {
        return id;
    }

    public String getProductId() {
        return productId;
    }

    public String getName() {
        return name;
    }

    public double getPrice() {
        return price;
    }

    public int getQuantity() {
        return quantity;
    }

    public String getImageUrl() {
        return imageUrl;
    }

    public void setQuantity(int quantity) {
        if (quantity < 0) {
            throw new IllegalArgumentException("Quantity cannot be negative");
        }
        this.quantity = quantity;
    }

    public double getTotalPrice() {
        return price * quantity;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        CartItem cartItem = (CartItem) o;
        return Objects.equals(productId, cartItem.productId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(productId);
    }
}