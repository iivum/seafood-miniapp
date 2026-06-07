package com.seafood.order.domain;

import com.seafood.shared.error.DomainException;

/**
 * 订单状态机(参见 design.md §6.3,specs/backend-api §Order lifecycle)。
 *
 * <p>合法转移(其它一律抛 DomainException):
 * <pre>
 *   PENDING → PAID → SHIPPED → COMPLETED
 *      ↓        ↓
 *   CANCELLED  CANCELLED
 * </pre>
 */
public sealed interface OrderStatus
        permits OrderStatus.Pending, OrderStatus.Paid, OrderStatus.Shipped,
                OrderStatus.Completed, OrderStatus.Cancelled {

    String code();

    record Pending() implements OrderStatus { public String code() { return "PENDING"; } }
    record Paid()    implements OrderStatus { public String code() { return "PAID"; } }
    record Shipped() implements OrderStatus { public String code() { return "SHIPPED"; } }
    record Completed() implements OrderStatus { public String code() { return "COMPLETED"; } }
    record Cancelled() implements OrderStatus { public String code() { return "CANCELLED"; } }

    /** 字符串 → 状态(API/DB 反序列化)。 */
    static OrderStatus of(String code) {
        return switch (code) {
            case "PENDING"   -> new Pending();
            case "PAID"      -> new Paid();
            case "SHIPPED"   -> new Shipped();
            case "COMPLETED" -> new Completed();
            case "CANCELLED" -> new Cancelled();
            default -> throw new DomainException("未知订单状态:" + code);
        };
    }

    /** 当前状态是否允许进入 target。集中规则,避免散落判断。 */
    default boolean canTransitionTo(OrderStatus target) {
        return switch (this) {
            case Pending p   -> target instanceof Paid || target instanceof Cancelled;
            case Paid pd     -> target instanceof Shipped || target instanceof Cancelled;
            case Shipped sh  -> target instanceof Completed;
            case Completed c -> false;
            case Cancelled c -> false;
        };
    }
}
