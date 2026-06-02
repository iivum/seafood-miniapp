package com.seafood.product.api.dto;

import java.math.BigDecimal;

public record ProductResponse(
    String id,
    String name,
    String description,
    BigDecimal price,
    int stock,
    String category,
    String imageUrl,
    boolean onSale
) {}
