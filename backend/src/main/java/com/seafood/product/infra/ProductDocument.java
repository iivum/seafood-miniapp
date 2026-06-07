package com.seafood.product.infra;

import com.seafood.product.domain.ProductStatus;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;

import java.math.BigDecimal;
import java.time.Instant;

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

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }

    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
}
