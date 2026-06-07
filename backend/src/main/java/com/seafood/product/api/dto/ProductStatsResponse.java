package com.seafood.product.api.dto;

import java.util.Map;

/** 管理后台 / 仪表盘用统计。 */
public record ProductStatsResponse(
        long total,
        long onSale,
        long outOfStock,
        Map<String, Long> byCategory
) {
}
