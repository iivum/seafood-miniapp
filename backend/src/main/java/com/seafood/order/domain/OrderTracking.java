package com.seafood.order.domain;

import com.seafood.shared.error.DomainException;

import java.util.List;

/**
 * 订单物流值对象(参见 design.md §6.3 + specs/admin-batch-operations §Logistics tracking)。
 *
 * <p>挂在 {@link Order} 上的不可变 record;Order 在 SHIPPED 状态之后才会有此值
 * (PENDING / PAID / COMPLETED / CANCELLED / REFUNDING 都不挂)。
 *
 * <p>字段:
 * <ul>
 *   <li>{@code carrier} — 物流公司名("顺丰" / "中通" / "京东" 等,自由字符串,2.5 / 4.3 走常量池);</li>
 *   <li>{@code trackingNumber} — 物流单号(由物流公司分配,14-30 位);</li>
 *   <li>{@code events} — 事件流,按时间正序(创建时校验;调用方保证,本构造不重新排序)。</li>
 * </ul>
 *
 * <p>业务约束:
 * <ul>
 *   <li>events 不能为空(挂在 Order 时至少有 1 条 "SHIPPED" 起始事件);</li>
 *   <li>carrier / trackingNumber 非空;</li>
 *   <li>本迭代(4.1)只引入值对象;真实数据由 4.3 / 4.4 admin 端录入(后端不动,后端只暴露
 *       GET /api/orders/{id}/tracking,见 4.2)。</li>
 * </ul>
 */
public record OrderTracking(
        String carrier,
        String trackingNumber,
        List<TrackingEvent> events
) {
    public OrderTracking {
        if (carrier == null || carrier.isBlank()) {
            throw new DomainException("物流公司不能为空");
        }
        if (trackingNumber == null || trackingNumber.isBlank()) {
            throw new DomainException("物流单号不能为空");
        }
        if (events == null || events.isEmpty()) {
            throw new DomainException("物流事件流不能为空");
        }
        events = List.copyOf(events);
    }
}
