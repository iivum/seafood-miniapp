package com.seafood.product.domain.model;

import com.seafood.common.domain.AggregateRoot;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.mongodb.core.mapping.Document;

import java.math.BigDecimal;

/**
 * Product aggregate root representing a seafood product in the catalog.
 * Follows Domain-Driven Design principles and encapsulates product-related business logic.
 * 
 * <p>Products are the core entities in the product catalog and contain
 * information such as name, description, price, stock, category, and image.</p>
 * 
 * @see AggregateRoot
 * @see org.springframework.data.mongodb.core.mapping.Document
 */
@Getter
@Setter
@NoArgsConstructor
@Document(collection = "products")
public class Product extends AggregateRoot<String> {
    
    /** Product name displayed to customers */
    private String name;
    
    /** Detailed product description */
    private String description;
    
    /** Product price in decimal format */
    private BigDecimal price;
    
    /** Current stock quantity available for sale */
    private int stock;
    
    /** Product category for filtering and organization */
    private String category;
    
    /** URL to product image in CDN or storage */
    private String imageUrl;
    
    /** Flag indicating if product is available for purchase */
    private boolean onSale;

    /**
     * Creates a new Product with the specified attributes.
     * The product is automatically set to on-sale status.
     *
     * @param name        Product display name (required)
     * @param description Detailed product description
     * @param price       Product price (must be non-negative)
     * @param stock       Initial stock quantity (must be non-negative)
     * @param category    Product category for organization
     * @param imageUrl    URL to product image
     * @throws IllegalArgumentException if name is null or empty
     */
    public Product(String name, String description, BigDecimal price, int stock, String category, String imageUrl) {
        super();
        if (name == null || name.trim().isEmpty()) {
            throw new IllegalArgumentException("Product name cannot be null or empty");
        }
        this.name = name;
        this.description = description;
        this.price = price;
        this.stock = stock;
        this.category = category;
        this.imageUrl = imageUrl;
        this.onSale = true;
    }

    /**
     * Increases the product stock by the specified quantity.
     * Used when restocking inventory.
     *
     * @param quantity Amount to add to current stock (can be negative to reduce)
     */
    public void updateStock(int quantity) {
        this.stock += quantity;
    }

    /**
     * Decreases the product stock by the specified quantity.
     * Used when an order is placed or stock is consumed.
     *
     * @param quantity Amount to reduce from current stock
     * @throws RuntimeException when stock would become negative (insufficient stock)
     */
    public void reduceStock(int quantity) {
        if (this.stock < quantity) {
            throw new RuntimeException("Insufficient stock");
        }
        this.stock -= quantity;
    }

    /**
     * Sets the product's on-sale status.
     * When false, product will not be displayed or be purchasable.
     *
     * @param onSale true to make product available, false to mark as unavailable
     */
    public void setOnSale(boolean onSale) {
        this.onSale = onSale;
    }
}
