package com.seafood.product.api.dto;

import java.math.BigDecimal;

public record UpdateProductRequest(
    String name,
    String description,
    BigDecimal price,
    Integer stock,
    String category,
    String imageUrl,
    Boolean onSale
) {}
