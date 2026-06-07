package com.seafood.bff.admin.dto;

import com.seafood.product.api.dto.ProductResponse;

/** 销量 Top N(决策 3.A:按 quantity)。 */
public record TopProductResponse(
        ProductResponse product,
        long totalQuantitySold
) {
}
