package com.seafood.order.api.dto;

import java.util.List;

public record OrderPageResponse(
    List<OrderSummaryResponse> orders,
    int page,
    int pageSize,
    long total,
    int totalPages
) {}
