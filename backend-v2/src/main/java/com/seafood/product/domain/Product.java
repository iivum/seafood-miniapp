package com.seafood.product.domain;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.math.BigDecimal;
import java.time.Instant;

@Document(collection = "products")
public record Product(
    @Id String id,
    String name,
    String description,
    BigDecimal price,
    int stock,
    @Indexed String category,
    String imageUrl,
    @Indexed boolean onSale,
    Instant createdAt,
    Instant updatedAt
) {
    public static Product create(String name, String description, BigDecimal price, int stock,
                                 String category, String imageUrl, boolean onSale, Instant now) {
        return new Product(null, name, description, price, stock, category, imageUrl, onSale, now, now);
    }
}
