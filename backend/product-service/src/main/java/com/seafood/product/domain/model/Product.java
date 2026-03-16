package com.seafood.product.domain.model;

import com.seafood.common.domain.AggregateRoot;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.mongodb.core.mapping.Document;

import java.math.BigDecimal;

/**
 * 商品聚合根 (Product Aggregate Root)
 * 遵循 DDD 设计原则，封装商品领域逻辑。
 */
@Getter
@Setter
@NoArgsConstructor
@Document(collection = "products")
public class Product extends AggregateRoot<String> {
    private String name;        // 商品名称
    private String description; // 商品描述
    private BigDecimal price;   // 商品价格
    private int stock;          // 商品库存
    private String category;    // 商品分类
    private String imageUrl;    // 商品图片地址
    private boolean onSale;     // 是否在售

    /**
     * 创建商品
     */
    public Product(String name, String description, BigDecimal price, int stock, String category, String imageUrl) {
        super();
        this.name = name;
        this.description = description;
        this.price = price;
        this.stock = stock;
        this.category = category;
        this.imageUrl = imageUrl;
        this.onSale = true;
    }

    /**
     * 更新库存 (增加)
     */
    public void updateStock(int quantity) {
        this.stock += quantity;
    }

    /**
     * 扣减库存 (减少)
     * @throws RuntimeException 当库存不足时抛出异常
     */
    public void reduceStock(int quantity) {
        if (this.stock < quantity) {
            throw new RuntimeException("Insufficient stock");
        }
        this.stock -= quantity;
    }

    /**
     * 设置上下架状态
     */
    public void setOnSale(boolean onSale) {
        this.onSale = onSale;
    }
}
