package com.seafood.product.domain;

import com.seafood.shared.error.DomainException;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Product 聚合根(参见 design.md §6.1)。
 *
 * <p>设计取舍:
 * <ul>
 *   <li>用 Java 25 record 表达不可变状态;可变操作返回新 record(避免 setter 漂移)</li>
 *   <li>业务规则(price &gt; 0、stock &ge; 0、name 非空)在构造时校验</li>
 *   <li>状态变更(下架、扣减库存)走命名方法,集中异常</li>
 * </ul>
 */
public record Product(
        String id,
        String name,
        String description,
        BigDecimal price,
        int stock,
        ProductCategory category,
        String imageUrl,
        ProductStatus status,
        Instant createdAt,
        Instant updatedAt
) {

    public Product {
        if (name == null || name.isBlank()) {
            throw new DomainException("商品名称不能为空");
        }
        if (price == null || price.signum() <= 0) {
            throw new DomainException("商品价格必须大于 0");
        }
        if (stock < 0) {
            throw new DomainException("库存不能为负数");
        }
        if (category == null) {
            throw new DomainException("商品分类不能为空");
        }
    }

    /** 扣减库存,返回新 record;库存不足抛 DomainException。 */
    public Product decrementStock(int quantity) {
        if (quantity <= 0) {
            throw new DomainException("扣减数量必须大于 0");
        }
        if (this.stock < quantity) {
            throw new DomainException("库存不足:仅剩 " + this.stock + ",需要 " + quantity);
        }
        return new Product(id, name, description, price, stock - quantity,
                category, imageUrl, status, createdAt, Instant.now());
    }

    /** 上架 / 下架。 */
    public Product withStatus(ProductStatus newStatus) {
        return new Product(id, name, description, price, stock, category, imageUrl,
                newStatus, createdAt, Instant.now());
    }

    /** 更新基础信息(name/desc/price/image),价格校验同上。 */
    public Product updateBasics(String name, String description, BigDecimal price, String imageUrl) {
        if (name == null || name.isBlank()) {
            throw new DomainException("商品名称不能为空");
        }
        if (price == null || price.signum() <= 0) {
            throw new DomainException("商品价格必须大于 0");
        }
        return new Product(id, name, description, price, stock, category, imageUrl,
                status, createdAt, Instant.now());
    }
}
