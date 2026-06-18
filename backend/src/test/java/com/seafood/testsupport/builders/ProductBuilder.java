package com.seafood.testsupport.builders;

import com.seafood.product.domain.Product;
import com.seafood.product.domain.ProductCategory;
import com.seafood.product.domain.ProductStatus;
import com.seafood.product.domain.Sku;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/**
 * ProductBuilder — D1 5 builder 之一。
 *
 * <p>核心字段:id / name / price / stock / category / status / imageUrl。
 * skus 默认空 list,需要时 withSkus() 添加。
 */
public final class ProductBuilder {

    private static final Instant DEFAULT_T = Instant.parse("2026-06-01T00:00:00Z");

    private String id = "p-test";
    private String name = "测试商品";
    private String description = "默认描述";
    private BigDecimal price = new BigDecimal("99.00");
    private int stock = 100;
    private ProductCategory category = new ProductCategory.Fish();
    private String imageUrl = "https://img.test/p-test.jpg";
    private ProductStatus status = ProductStatus.ACTIVE;
    private List<Sku> skus = List.of();
    private Instant createdAt = DEFAULT_T;
    private Instant updatedAt = DEFAULT_T;

    private ProductBuilder() {}

    public static ProductBuilder aProduct() {
        return new ProductBuilder();
    }

    public ProductBuilder withId(String id) { this.id = id; return this; }
    public ProductBuilder withName(String name) { this.name = name; return this; }
    public ProductBuilder withDescription(String description) { this.description = description; return this; }
    public ProductBuilder withPrice(BigDecimal price) { this.price = price; return this; }
    public ProductBuilder withStock(int stock) { this.stock = stock; return this; }
    public ProductBuilder withCategory(ProductCategory category) { this.category = category; return this; }
    public ProductBuilder withImageUrl(String imageUrl) { this.imageUrl = imageUrl; return this; }
    public ProductBuilder withStatus(ProductStatus status) { this.status = status; return this; }
    public ProductBuilder withSkus(List<Sku> skus) { this.skus = skus; return this; }

    public Product build() {
        return new Product(id, name, description, price, stock, category,
            imageUrl, status, skus, createdAt, updatedAt);
    }
}