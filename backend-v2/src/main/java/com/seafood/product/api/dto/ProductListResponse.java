package com.seafood.product.api.dto;

import java.util.List;

public record ProductListResponse(
    List<ProductResponse> products,
    int page,
    int pageSize,
    long totalProducts,
    int totalPages,
    boolean hasNext,
    boolean hasPrev
) {}
