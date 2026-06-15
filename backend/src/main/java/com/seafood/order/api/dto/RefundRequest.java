package com.seafood.order.api.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

/**
 * 退款申请请求体(参见 design.md §6.3 + specs/admin-batch-operations §Refund lifecycle)。
 *
 * <p>路线图 4.7 — mp-08 底部 sheet 提交。
 * 字段约束:
 * <ul>
 *   <li>{@code amount} — 必填,严格 > 0(由 Order 总额上限校验在 Service 层,不在 DTO)</li>
 *   <li>{@code reason} — 必填,1..200 字符(Refund 聚合根 compact constructor 二次校验)</li>
 * </ul>
 */
public record RefundRequest(
        @NotNull @DecimalMin(value = "0.01", message = "退款金额必须大于 0") BigDecimal amount,
        @NotNull @Size(min = 1, max = 200, message = "退款原因 1..200 字符") String reason
) {
}
