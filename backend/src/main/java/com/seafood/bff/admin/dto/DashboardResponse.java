package com.seafood.bff.admin.dto;

import com.seafood.product.api.dto.ProductStatsResponse;

import java.util.List;

/** 仪表盘聚合(参见 design.md §5.1 GET /api/admin/dashboard)。 */
public record DashboardResponse(
        OrderStatsResponse orderStats,
        ProductStatsResponse productStats,
        List<TopProductResponse> topProducts
) {
}
