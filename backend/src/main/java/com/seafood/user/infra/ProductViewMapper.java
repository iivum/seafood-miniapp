package com.seafood.user.infra;

import com.seafood.user.domain.ProductView;

public final class ProductViewMapper {

    private ProductViewMapper() {}

    public static ProductView toDomain(ProductViewDocument d) {
        if (d == null) return null;
        return new ProductView(d.getId(), d.getUserId(), d.getProductId(), d.getViewedAt());
    }
}
