package com.seafood.bff.admin.dto;

/** 订单数量统计 — 决策 2.A:数量而非金额。 */
public record OrderStatsResponse(
        long today,
        long week,
        long month
) {
}
