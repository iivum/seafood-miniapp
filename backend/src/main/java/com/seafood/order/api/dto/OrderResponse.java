package com.seafood.order.api.dto;

import com.seafood.order.domain.Order;
import com.seafood.order.domain.OrderItem;
import com.seafood.order.domain.OrderTracking;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/**
 * 订单响应 DTO(参见 specs/backend-api §Order lifecycle)。
 *
 * <p>v2 视觉 / admin-ui 路线图 4.20:本 DTO 暴露 admin-ui OD 期望的 2 个可选字段 —
 * {@code tracking}(4.1 物流,SHIPPED 之后挂值;PENDING / PAID / CANCELLED 为 null)和
 * {@code refundId}(4.7 退款单 id,REFUNDING 状态时挂值,其它状态 null)。前端 OD
 * 详情页 / 时间线 / 退款入口依赖这两个字段。
 *
 * <p>保持 record 不可变 + from() 静态工厂;业务字段均来自 {@link Order} 聚合根,
 * 不暴露 OrderDocument 内部结构。
 */
public record OrderResponse(
        String id,
        String userId,
        List<OrderItem> items,
        BigDecimal totalAmount,
        String status,
        String cancelReason,
        OrderTracking tracking,
        String refundId,
        Instant createdAt,
        Instant updatedAt
) {
    public static OrderResponse from(Order o) {
        return new OrderResponse(
                o.id(), o.userId(), o.items(), o.totalAmount(),
                o.status().code(), o.cancelReason(),
                o.tracking(), o.refundId(),
                o.createdAt(), o.updatedAt());
    }
}
