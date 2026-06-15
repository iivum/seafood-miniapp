package com.seafood.product.infra;

import com.seafood.product.domain.ProductStatus;
import com.seafood.product.domain.Sku;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/**
 * products collection(参见 design.md §6.1)。
 * 文本索引(name+description)在 {@code MongoIndexInitializer} 启动时显式建,
 * 避免 {@code @TextIndex} 注解在不同 Spring Data MongoDB 版本下位置变化。
 */
@Document(collection = "products")
public class ProductDocument {

    @Id
    private String id;

    @Indexed
    private String name;

    private String description;
    private BigDecimal price;

    private int stock;

    @Indexed
    private String category;     // 存 displayName,经 ProductCategory.of 反序列化

    private String imageUrl;

    @Indexed
    private boolean onSale;      // = status == ACTIVE

    @Field("status")
    private ProductStatus status;

    /**
     * SKU 列表(3.7 新增,nullable,空 list 等价"只用默认 SKU")。
     * MongoDB 缺省不写字段,旧数据反序列化为 null(由 Product 紧凑构造器兜底转空 list)。
     * 单 SKU ≤ 200B,默认 50 个 SKU 上限远低于 Mongo doc 16MB 限制。
     */
    private List<Sku> skus;

    @Indexed(direction = org.springframework.data.mongodb.core.index.IndexDirection.DESCENDING)
    private Instant createdAt;

    private Instant updatedAt;

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public BigDecimal getPrice() { return price; }
    public void setPrice(BigDecimal price) { this.price = price; }

    public int getStock() { return stock; }
    public void setStock(int stock) { this.stock = stock; }

    public String getCategory() { return category; }
    public void setCategory(String category) { this.category = category; }

    public String getImageUrl() { return imageUrl; }
    public void setImageUrl(String imageUrl) { this.imageUrl = imageUrl; }

    public boolean isOnSale() { return onSale; }
    public void setOnSale(boolean onSale) { this.onSale = onSale; }

    public ProductStatus getStatus() { return status; }
    public void setStatus(ProductStatus status) { this.status = status; }

    public List<Sku> getSkus() { return skus; }
    public void setSkus(List<Sku> skus) { this.skus = skus; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }

    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
}
