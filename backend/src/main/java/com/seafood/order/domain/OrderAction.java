package com.seafood.order.domain;

import java.util.Map;
import java.util.Set;

/**
 * 客户侧订单状态机操作(sprint-1-closure 1.1-1.3)。
 *
 * <p>每个 {@link OrderAction} 关联一个 {@link TransitionRule} 描述:
 * <ul>
 *   <li>{@code allowedFrom}:当前状态必须在该集合中(否则 409 INVALID_STATE)</li>
 *   <li>{@code target}:执行成功后订单的新状态(若为 {@code null} 表示无状态变更,如 REMIND_SHIP / REBUY)</li>
 *   <li>{@code metricName}:埋点 counter 名(如 {@code orders.cancelled});{@code null} 表示不埋点</li>
 * </ul>
 *
 * <p>新增 action 只需:1) 加 enum 常量;2) 在 {@link #RULES} 加一条规则;3) 在
 * {@link com.seafood.order.application.OrderService#transition} 加 case 分支。
 */
public enum OrderAction {
    CANCEL,
    PAY,
    CONFIRM_RECEIVE,
    REBUY,
    REFUND,
    REMIND_SHIP;

    /**
     * 状态机集中规则表。任何"当前状态能否走 action"的判断都走这里,避免散落。
     */
    public record TransitionRule(
            Set<Class<? extends OrderStatus>> allowedFrom,
            Class<? extends OrderStatus> target,
            String metricName) {
    }

    public static final Map<OrderAction, TransitionRule> RULES = Map.of(
            CANCEL, new TransitionRule(
                    Set.of(OrderStatus.Pending.class),
                    OrderStatus.Cancelled.class,
                    "orders.cancelled"),
            PAY, new TransitionRule(
                    Set.of(OrderStatus.Pending.class),
                    OrderStatus.Paid.class,
                    "orders.paid"),
            CONFIRM_RECEIVE, new TransitionRule(
                    Set.of(OrderStatus.Shipped.class),
                    OrderStatus.Completed.class,
                    "orders.completed"),
            REBUY, new TransitionRule(
                    Set.of(OrderStatus.Completed.class, OrderStatus.Cancelled.class, OrderStatus.Refunded.class),
                    null,  // no state change
                    "orders.rebuy"),
            REFUND, new TransitionRule(
                    Set.of(OrderStatus.Paid.class, OrderStatus.Shipped.class, OrderStatus.Completed.class),
                    OrderStatus.Refunding.class,
                    "orders.refunding"),
            REMIND_SHIP, new TransitionRule(
                    Set.of(OrderStatus.Paid.class),
                    null,  // no state change
                    "orders.remind_ship")
    );

    public TransitionRule rule() {
        return RULES.get(this);
    }

    public boolean isAllowedFrom(OrderStatus current) {
        return rule().allowedFrom().stream().anyMatch(c -> c.isInstance(current));
    }
}
