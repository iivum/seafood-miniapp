package com.seafood.bff.admin.dto;

import java.time.LocalDate;

/**
 * 仪表盘 7 天趋势点(路线图 2.17)。
 * <p>date 字段用 LocalDate(UTC+8 当日),count = 当日 0 点至次日 0 点的订单数。
 * 不存时间戳 — admin UI 折线图 X 轴只关心「哪一天」。
 */
public record TrendPointResponse(
        LocalDate date,
        long count
) {
}
