package com.seafood.product.api.dto;

import com.seafood.product.domain.Product;
import com.seafood.product.domain.ProductStatus;

import java.math.BigDecimal;
import java.time.Instant;

/** 商品对外响应。 */
public record ProductResponse(
        String id,
        String name,
        String description,
        BigDecimal price,
        int stock,
        String category,
        String imageUrl,
        ProductStatus status,
        Instant createdAt,
        Instant updatedAt
) {
    public static ProductResponse from(Product p) {
        return new ProductResponse(
                p.id(), p.name(), p.description(), p.price(), p.stock(),
                p.category().displayName(), p.imageUrl(), p.status(),
                p.createdAt(), p.updatedAt());
    }
}
