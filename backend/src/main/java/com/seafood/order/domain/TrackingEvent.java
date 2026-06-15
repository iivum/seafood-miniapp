package com.seafood.order.domain;

import com.seafood.shared.error.DomainException;

import java.time.Instant;

/**
 * 物流跟踪单事件值对象(参见 design.md §6.3 + specs/admin-batch-operations §Logistics tracking)。
 *
 * <p>一次物流事件(发货 / 到达中转站 / 派送中 / 签收 等):
 * <ul>
 *   <li>{@code at} — 事件时间(物流公司推送的时间戳,UTC instant)</li>
 *   <li>{@code status} — 事件状态描述,例如 "SHIPPED" / "IN_TRANSIT" / "DELIVERED"(可扩展,本期未限枚举)</li>
 *   <li>{@code location} — 事件发生地(城市或中转站)</li>
 *   <li>{@code description} — 人类可读描述,最长 200 字符</li>
 * </ul>
 *
 * <p>不可变 record;所有字段在构造时校验。
 */
public record TrackingEvent(
        Instant at,
        String status,
        String location,
        String description
) {
    public TrackingEvent {
        if (at == null) {
            throw new DomainException("物流事件时间不能为空");
        }
        if (status == null || status.isBlank()) {
            throw new DomainException("物流事件状态不能为空");
        }
        if (description == null) {
            throw new DomainException("物流事件描述不能为空");
        }
        if (description.length() > 200) {
            throw new DomainException("物流事件描述超过 200 字符上限");
        }
    }
}
