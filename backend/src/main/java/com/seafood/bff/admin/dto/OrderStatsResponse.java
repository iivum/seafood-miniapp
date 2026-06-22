package com.seafood.bff.admin.dto;

import java.math.BigDecimal;

/** 订单统计 — 今日/本周/本月数量 + 今日 GMV / 客单价。 */
public record OrderStatsResponse(
        long today,
        long week,
        long month,
        BigDecimal gmvToday,
        BigDecimal avgOrderToday
) {
}
