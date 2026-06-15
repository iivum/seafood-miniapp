package com.seafood.product.domain;

import com.seafood.shared.error.DomainException;

import java.math.BigDecimal;
import java.util.Map;

/**
 * 商品 SKU 值对象(路线图 3.7 — ad-04 商品表单 SKU 行内编辑基础)。
 *
 * <p>SKU = Stock Keeping Unit,标识商品某一规格变种(颜色 / 尺寸 / 净含量 等)。
 * 例如"三文鱼 200g"和"三文鱼 500g"是两个 SKU,共享一个 Product(同名 / 同描述 / 同分类)
 * 但价格 / 库存不同。
 *
 * <p>不可变 record + 紧凑构造器校验。验证规则(参见 ProductService.addSku 复用):
 * <ul>
 *   <li>{@code name} 非空 / ≤ 100 字符</li>
 *   <li>{@code price} > 0</li>
 *   <li>{@code stock} >= 0</li>
 *   <li>{@code sortOrder} 0-99(列表展示顺序)</li>
 *   <li>{@code specs} 可空(空 map = 不分类),key / value 非空</li>
 * </ul>
 *
 * <p>设计要点:**向前兼容**(参见 tasks.md §5.13)— {@code Product.price} / {@code stock}
 * 保留作"默认 SKU"字段;当 {@code Product.skus = []} 时,UI / API 读 price / stock;
 * 当 skus 非空时,第一 SKU 的 price / stock 与 Product.price / stock 同步(避免不一致)。
 */
public record Sku(
        String id,
        String name,
        Map<String, String> specs,
        BigDecimal price,
        int stock,
        int sortOrder
) {
    public Sku {
        if (name == null || name.isBlank()) {
            throw new DomainException("SKU 名称不能为空");
        }
        if (name.length() > 100) {
            throw new DomainException("SKU 名称超过 100 字符上限");
        }
        if (price == null || price.signum() <= 0) {
            throw new DomainException("SKU 价格必须大于 0");
        }
        if (stock < 0) {
            throw new DomainException("SKU 库存不能为负数");
        }
        if (sortOrder < 0 || sortOrder > 99) {
            throw new DomainException("SKU 排序必须在 0-99 之间");
        }
        // specs:null 等价空 map;key / value 都不能 null
        if (specs == null) {
            specs = Map.of();
        } else {
            specs = Map.copyOf(specs);
            for (var e : specs.entrySet()) {
                if (e.getKey() == null || e.getKey().isBlank()) {
                    throw new DomainException("SKU 规格 key 不能为空");
                }
                if (e.getValue() == null) {
                    throw new DomainException("SKU 规格 value 不能为 null,空字符串请传 \"\"");
                }
            }
        }
    }
}
