package com.seafood.bff.admin.dto;

import com.seafood.order.api.dto.OrderResponse;
import com.seafood.product.api.dto.ProductResponse;
import com.seafood.product.api.dto.ProductStatsResponse;

import java.util.List;

/**
 * 仪表盘聚合(参见 design.md §5.1 GET /api/admin/dashboard)。
 *
 * <p>路线图 2.17 / 2.18 / 2.21 扩展:
 * <ul>
 *   <li>{@code trend7d}  — 7 天订单数折线(2.17)</li>
 *   <li>{@code lowStock} — 库存 < 10 Top 10 预警列表(2.18)</li>
 *   <li>{@code recentOrders} — 最近 10 单流(2.21)</li>
 * </ul>
 */
public record DashboardResponse(
        OrderStatsResponse orderStats,
        ProductStatsResponse productStats,
        List<TopProductResponse> topProducts,
        List<TrendPointResponse> trend7d,
        List<ProductResponse> lowStock,
        List<OrderResponse> recentOrders
) {
}
