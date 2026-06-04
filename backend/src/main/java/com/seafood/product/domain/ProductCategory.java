package com.seafood.product.domain;

import com.seafood.shared.error.DomainException;

/**
 * 商品分类(参见 design.md §6.1 字段 category + §2 决策"沿用中文分类")。
 * 用 sealed interface 限定可选分类,新增分类必须显式扩展并匹配 MongoDB 中已存在的数据。
 */
public sealed interface ProductCategory
        permits ProductCategory.Fish, ProductCategory.Shrimp, ProductCategory.Shell,
        ProductCategory.Mollusk, ProductCategory.Seaweed {

    String displayName();

    record Fish() implements ProductCategory {
        public String displayName() { return "鱼类"; }
    }
    record Shrimp() implements ProductCategory {
        public String displayName() { return "虾蟹"; }
    }
    record Shell() implements ProductCategory {
        public String displayName() { return "贝类"; }
    }
    record Mollusk() implements ProductCategory {
        public String displayName() { return "软体"; }
    }
    record Seaweed() implements ProductCategory {
        public String displayName() { return "海藻"; }
    }

    /** 中文名 → 分类(用于 API 反序列化 / seed 导入)。 */
    static ProductCategory of(String displayName) {
        return switch (displayName) {
            case "鱼类" -> new Fish();
            case "虾蟹" -> new Shrimp();
            case "贝类" -> new Shell();
            case "软体" -> new Mollusk();
            case "海藻" -> new Seaweed();
            default -> throw new DomainException("未知分类:" + displayName);
        };
    }
}
