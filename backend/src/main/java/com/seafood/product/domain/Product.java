package com.seafood.product.domain;

import com.seafood.shared.error.DomainException;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * Product 聚合根(参见 design.md §6.1)。
 *
 * <p>设计取舍:
 * <ul>
 *   <li>用 Java 25 record 表达不可变状态;可变操作返回新 record(避免 setter 漂移)</li>
 *   <li>业务规则(price &gt; 0、stock &ge; 0、name 非空)在构造时校验</li>
 *   <li>状态变更(下架、扣减库存)走命名方法,集中异常</li>
 * </ul>
 *
 * <p>路线图 3.7(Sprint 2):新增 {@code skus: List<Sku>} 字段。{@code price} /
 * {@code stock} 保留作"默认 SKU"(参见 tasks.md §5.13 决策),旧数据反序列化为
 * {@code skus = []} 时,UI / API 走默认 SKU 逻辑(不引入新端点)。聚合校验:
 * skus 非空时,第一 SKU 的 price / stock 应等于 Product.price / stock(避免不一致);
 * skus 为空时,price / stock 必须 > 0(防全空商品)。
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
        List<Sku> skus,
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
        // skus 字段校验:null 等价空 list;非空时第一 SKU 应与默认 price/stock 一致
        if (skus == null) {
            skus = List.of();
        } else {
            skus = List.copyOf(skus);
            if (!skus.isEmpty()) {
                Sku first = skus.get(0);
                if (first.price().compareTo(price) != 0) {
                    throw new DomainException("第一 SKU 价格(" + first.price()
                            + ")必须等于商品默认价格(" + price + ")");
                }
                if (first.stock() != stock) {
                    throw new DomainException("第一 SKU 库存(" + first.stock()
                            + ")必须等于商品默认库存(" + stock + ")");
                }
            }
        }
    }

    /** 旧构造器(11 参)— 不带 skus,等价 skus = []。供 ProductService.create() 等存量调用。 */
    public Product(String id, String name, String description, BigDecimal price, int stock,
                   ProductCategory category, String imageUrl, ProductStatus status,
                   Instant createdAt, Instant updatedAt) {
        this(id, name, description, price, stock, category, imageUrl, status, List.of(),
                createdAt, updatedAt);
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
                category, imageUrl, status, skus, createdAt, Instant.now());
    }

    /** 上架 / 下架。 */
    public Product withStatus(ProductStatus newStatus) {
        return new Product(id, name, description, price, stock, category, imageUrl,
                newStatus, skus, createdAt, Instant.now());
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
                status, skus, createdAt, Instant.now());
    }

    /**
     * 3.7 命名方法:替换整张 SKU 列表(批量更新,ad-04 SKU 行内编辑保存)。
     * 校验:数量 0-50(防止意外大数组撑爆 Mongo doc 16MB 限制);
     * 第一 SKU 的 price / stock 与 Product 默认值必须一致(聚合不变量)。
     */
    public Product replaceSkus(List<Sku> newSkus) {
        if (newSkus == null) {
            newSkus = List.of();
        }
        if (newSkus.size() > 50) {
            throw new DomainException("SKU 数量超过 50 上限");
        }
        // 紧凑构造器会校验第一 SKU 与默认 price/stock 一致;这里预读一次
        if (!newSkus.isEmpty()) {
            Sku first = newSkus.get(0);
            if (first.price().compareTo(price) != 0) {
                throw new DomainException("第一 SKU 价格必须等于商品默认价格");
            }
            if (first.stock() != stock) {
                throw new DomainException("第一 SKU 库存必须等于商品默认库存");
            }
        }
        return new Product(id, name, description, price, stock, category, imageUrl,
                status, newSkus, createdAt, Instant.now());
    }
}
