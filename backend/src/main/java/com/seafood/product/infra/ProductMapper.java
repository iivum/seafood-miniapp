package com.seafood.product.infra;

import com.seafood.product.domain.Product;
import com.seafood.product.domain.ProductCategory;
import com.seafood.product.domain.ProductStatus;

import java.time.Instant;

/** ProductDocument ↔ Product 域对象映射。 */
public final class ProductMapper {

    private ProductMapper() {}

    public static Product toDomain(ProductDocument d) {
        if (d == null) return null;
        return new Product(
                d.getId(),
                d.getName(),
                d.getDescription(),
                d.getPrice(),
                d.getStock(),
                ProductCategory.of(d.getCategory()),
                d.getImageUrl(),
                d.getStatus(),
                d.getCreatedAt(),
                d.getUpdatedAt());
    }

    public static ProductDocument toDocument(Product p) {
        ProductDocument d = new ProductDocument();
        d.setId(p.id());
        d.setName(p.name());
        d.setDescription(p.description());
        d.setPrice(p.price());
        d.setStock(p.stock());
        d.setCategory(p.category().displayName());
        d.setImageUrl(p.imageUrl());
        d.setStatus(p.status());
        d.setOnSale(p.status() == ProductStatus.ACTIVE);
        d.setCreatedAt(p.createdAt() == null ? Instant.now() : p.createdAt());
        d.setUpdatedAt(p.updatedAt() == null ? Instant.now() : p.updatedAt());
        return d;
    }
}
