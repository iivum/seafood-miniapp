package com.seafood.bff.admin.dto;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

import java.util.List;

/**
 * 批量发货请求体(参见 design.md §5.1 + specs/admin-batch-operations §Bulk ship)。
 *
 * <p>路线图 4.13 — ad-05 订单列表「批量发货」按钮:勾选若干订单 → 填统一物流(可选)
 * → 提交。一次最多 50 单(防 admin 误操作刷出巨量更新)。
 *
 * <p>{@code orderIds} 非空且 ≤ 50;{@code carrier} / {@code trackingNumber} 可选 —
 * 不填时只把状态 PAID → SHIPPED(后续 admin 端可单独录物流,见 4.18 ad-06 详情);
 * 填了则同步挂到 Order.tracking 字段(参见 4.1 OrderTracking 值对象)。
 */
public record BatchShipRequest(
        @NotEmpty(message = "至少需要一个订单 ID")
        @Size(max = 50, message = "单次批量最多 50 单") List<String> orderIds,
        @Size(max = 50) String carrier,
        @Size(max = 50) String trackingNumber
) {
}
