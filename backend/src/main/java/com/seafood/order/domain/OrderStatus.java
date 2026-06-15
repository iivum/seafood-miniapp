package com.seafood.order.domain;

import com.seafood.shared.error.DomainException;

/**
 * 订单状态机(参见 design.md §6.3,specs/backend-api §Order lifecycle)。
 *
 * <p>完整合法转移(Sprint 3 4.7 / 4.8 补完后):
 * <pre>
 *   PENDING → PAID → SHIPPED → COMPLETED → REFUNDING → REFUNDED(终态)
 *      ↓        ↓                       ↑
 *   CANCELLED  CANCELLED                (Sprint 3 4.7 — mp 申请退款)
 *                                     (admin 拒绝 4.8:REFUNDING → COMPLETED)
 * </pre>
 *
 * <p>历史:
 * <ul>
 *   <li>2.6 引入 {@link Refunding} 状态值(M-3 准备,无出/入转换)</li>
 *   <li>4.7 补 {@code COMPLETED → REFUNDING} 入转换(mp 申请退款),并引入 {@link Refunded} 终态
 *       供 admin 同意路径使用</li>
 *   <li>4.8 补 {@code REFUNDING → REFUNDED}(admin 同意)/ {@code REFUNDING → COMPLETED}
 *       (admin 拒绝)。admin 拒绝时回到 COMPLETED 而不是 CANCELLED,因为订单本身已经完成
 *       履约,只是"没退成";业务上继续走售后期</li>
 * </ul>
 */
public sealed interface OrderStatus
        permits OrderStatus.Pending, OrderStatus.Paid, OrderStatus.Shipped,
                OrderStatus.Completed, OrderStatus.Cancelled,
                OrderStatus.Refunding, OrderStatus.Refunded {

    String code();

    record Pending()   implements OrderStatus { public String code() { return "PENDING"; } }
    record Paid()      implements OrderStatus { public String code() { return "PAID"; } }
    record Shipped()   implements OrderStatus { public String code() { return "SHIPPED"; } }
    record Completed() implements OrderStatus { public String code() { return "COMPLETED"; } }
    record Cancelled() implements OrderStatus { public String code() { return "CANCELLED"; } }
    /**
     * 退款中(2.6 引入)。订单已 COMPLETED,等待 admin 审核退款申请 → {@link Refunded}。
     * 申请入口:4.7(mp 端)将 {@code COMPLETED} 转 {@code REFUNDING};
     * 拒绝回退:4.8 admin 拒绝时 {@code REFUNDING → COMPLETED}。
     */
    record Refunding() implements OrderStatus { public String code() { return "REFUNDING"; } }
    /**
     * 已退款(4.7 引入,终态)。admin 同意退款后落到此状态 — 不可再流转。
     * 与 {@link Cancelled} 终态并列。
     */
    record Refunded()  implements OrderStatus { public String code() { return "REFUNDED"; } }

    /** 字符串 → 状态(API/DB 反序列化)。 */
    static OrderStatus of(String code) {
        return switch (code) {
            case "PENDING"   -> new Pending();
            case "PAID"      -> new Paid();
            case "SHIPPED"   -> new Shipped();
            case "COMPLETED" -> new Completed();
            case "CANCELLED" -> new Cancelled();
            case "REFUNDING" -> new Refunding();
            case "REFUNDED"  -> new Refunded();
            default -> throw new DomainException("未知订单状态:" + code);
        };
    }

    /** 当前状态是否允许进入 target。集中规则,避免散落判断。 */
    default boolean canTransitionTo(OrderStatus target) {
        return switch (this) {
            case Pending _   -> target instanceof Paid || target instanceof Cancelled;
            case Paid _      -> target instanceof Shipped || target instanceof Cancelled;
            case Shipped _   -> target instanceof Completed;
            // 4.7 补:COMPLETED → REFUNDING(mp 申请退款)
            case Completed _ -> target instanceof Refunding;
            case Cancelled _ -> false;
            // 4.8 补:REFUNDING → REFUNDED(admin 同意) / REFUNDING → COMPLETED(admin 拒绝回退)
            case Refunding _ -> target instanceof Refunded || target instanceof Completed;
            case Refunded _  -> false;
        };
    }
}
