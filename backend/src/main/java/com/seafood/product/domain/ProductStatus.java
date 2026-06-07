package com.seafood.product.domain;

/**
 * 商品状态。{@code ACTIVE} = 在售,其它不出现在公共列表。
 */
public enum ProductStatus {
    ACTIVE,
    OUT_OF_STOCK,
    DISCONTINUED
}
