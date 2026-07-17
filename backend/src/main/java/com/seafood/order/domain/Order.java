package com.seafood.order.domain;

import com.seafood.shared.error.DomainException;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/**
 * Order 聚合根(参见 design.md §6.3 + §6.1 orders schema + specs/admin-batch-operations)。
 *
 * <p>状态变更走命名方法,集中规则;外部只读状态用 {@code status()}。
 *
 * <p>Sprint 3 4.1:新增 {@code tracking} 字段(物流值对象,SHIPPED 之后才有值),
 * 用于 4.3 mp 端时间线 / 4.4 admin 端时间线组件 / 4.5 E2E「发货 → 查物流 → 时间线可见」。
 */
public record Order(
        String id,
        String userId,
        List<OrderItem> items,
        BigDecimal subtotal,
        BigDecimal shippingFee,
        BigDecimal discount,
        BigDecimal totalAmount,
        OrderStatus status,
        String cancelReason,
        OrderTracking tracking,
        String refundId,
        Instant estimatedDelivery,
        Instant createdAt,
        Instant updatedAt
) {

    public Order {
        if (userId == null || userId.isBlank()) {
            throw new DomainException("userId 不能为空");
        }
        if (items == null || items.isEmpty()) {
            throw new DomainException("订单必须至少包含一行");
        }
        items = List.copyOf(items);
        // fix-order-amount-contract:历史订单(改动前创建)缺这 3 个字段,读取时按 0
        // 兜底(design.md 决策 3——不回填历史数据,totalAmount 保持原值不变)。
        if (subtotal == null) {
            subtotal = BigDecimal.ZERO;
        }
        if (shippingFee == null) {
            shippingFee = BigDecimal.ZERO;
        }
        if (discount == null) {
            discount = BigDecimal.ZERO;
        }
        if (totalAmount == null || totalAmount.signum() <= 0) {
            throw new DomainException("订单金额必须大于 0");
        }
        if (status == null) {
            throw new DomainException("订单状态不能为空");
        }
        // tracking 字段(4.1 新增)校验:有值时必须挂在 SHIPPED 之后的状态。
        // COMPLETED / REFUNDING / REFUNDED 状态保留 tracking — 用户/客服可能想看历史物流。
        // 其它(PENDING / PAID / CANCELLED)理论上不应有 tracking,误传就拒。
        if (tracking != null && !(status instanceof OrderStatus.Shipped
                || status instanceof OrderStatus.Completed
                || status instanceof OrderStatus.Refunding
                || status instanceof OrderStatus.Refunded)) {
            throw new DomainException("仅 SHIPPED/COMPLETED/REFUNDING/REFUNDED 订单可挂物流,当前:" + status.code());
        }
        // refundId(4.20 新增)校验:仅 REFUNDING / REFUNDED 状态挂值,
        // 其它状态有值即拒。空字符串视同 null。
        if (refundId != null && !refundId.isBlank()
                && !(status instanceof OrderStatus.Refunding || status instanceof OrderStatus.Refunded)) {
            throw new DomainException("仅 REFUNDING/REFUNDED 订单可挂退款单 id,当前:" + status.code());
        }
        if (refundId != null && refundId.isBlank()) {
            refundId = null;
        }
    }

    /** PENDING → PAID。 */
    public Order markPaid(Instant when) {
        requireTransition(OrderStatus.Paid.class);
        return mutate(new OrderStatus.Paid(), null, null, null, when);
    }

    /** PAID → SHIPPED。 */
    public Order markShipped(Instant when) {
        requireTransition(OrderStatus.Shipped.class);
        return mutate(new OrderStatus.Shipped(), null, null, null, when);
    }

    /** SHIPPED → COMPLETED。 */
    public Order markCompleted(Instant when) {
        requireTransition(OrderStatus.Completed.class);
        return mutate(new OrderStatus.Completed(), null, null, null, when);
    }

    /**
     * 4.7:COMPLETED → REFUNDING(mp 申请退款,先在 Service 层做金额/状态/owner 校验,
     * 这里只负责状态机)。{@code tracking} 字段保留:用户可能想看历史物流。
     */
    public Order markRefunding(Instant when) {
        requireTransition(OrderStatus.Refunding.class);
        return mutate(new OrderStatus.Refunding(), null, tracking, refundId, when);
    }

    /**
     * 4.8:REFUNDING → REFUNDED(admin 同意,终态)。{@code tracking} 保留(售后期查询用)。
     */
    public Order markRefunded(Instant when) {
        requireTransition(OrderStatus.Refunded.class);
        return mutate(new OrderStatus.Refunded(), null, tracking, refundId, when);
    }

    /**
     * 4.8:REFUNDING → COMPLETED(admin 拒绝退款,订单回退到"已签收"业务态)。
     * 之所以回退到 COMPLETED 而不是 CANCELLED:订单本身履约已完成,只是"没退成";
     * 业务上仍可走售后期(补发/换货),与 cancel 语义不同。
     * 拒绝时同时清空 refundId,退回无退款单关联状态。
     */
    public Order markRefundRejected(Instant when) {
        requireTransition(OrderStatus.Completed.class);
        return mutate(new OrderStatus.Completed(), null, tracking, null, when);
    }

    /** 任意可取消状态 → CANCELLED。 */
    public Order cancel(String reason, Instant when) {
        if (!(status instanceof OrderStatus.Pending || status instanceof OrderStatus.Paid)) {
            throw new DomainException("仅 PENDING/PAID 订单可取消,当前:" + status.code());
        }
        return mutate(new OrderStatus.Cancelled(), reason == null ? "" : reason, null, null, when);
    }

    /**
     * 4.1 命名方法:挂载物流信息。仅 SHIPPED / COMPLETED 状态可挂(校验在 record compact
     * constructor 内),其他状态抛 DomainException。
     *
     * <p>本迭代(4.1)只引入入口;Sprint 3 4.3 / 4.4 UI 端 / 4.5 E2E 走完后,管理端
     * 「录入物流单号」按钮会调 admin 端 POST 端点(后续 4.13 批量发货时也可能带物流单号)。
     */
    public Order attachTracking(OrderTracking tracking) {
        if (tracking == null) {
            throw new DomainException("物流值对象不能为 null,需要清空请传 null 给 clearTracking");
        }
        return mutate(status, cancelReason, tracking, refundId, Instant.now());
    }

    /** 清空物流(异常路径,Sprint 3 可能用不到,先留入口)。 */
    public Order clearTracking() {
        return mutate(status, cancelReason, null, refundId, Instant.now());
    }

    /**
     * 4.20 命名方法:挂载退款单 id。{@code requestRefund} 成功路径由 Service 层
     * 在 {@code markRefunding} 后调用,挂上 Refund 聚合根的 id;校验在 record compact
     * constructor 内,仅 REFUNDING/REFUNDED 状态可挂。
     */
    public Order attachRefundId(String newRefundId) {
        if (newRefundId == null || newRefundId.isBlank()) {
            throw new DomainException("退款单 id 不能为空,需要清空请传 null 给 clearRefundId");
        }
        return mutate(status, cancelReason, tracking, newRefundId, Instant.now());
    }

    /** 清空退款单 id(异常路径,markRefundRejected 内部使用)。 */
    public Order clearRefundId() {
        return mutate(status, cancelReason, tracking, null, Instant.now());
    }

    /**
     * mp-09 路线图 4.20:挂载预计送达时间(Estimated Time of Arrival)。
     *
     * <p>约定:{@code estimatedDelivery} 在 create 时由 Service 层计算为 {@code now + 24h};
     * 后续状态机(markPaid / markShipped / markCompleted)保持不变 — ETA 一次写入,持久跟随订单。
     * 显式传 {@code null} 表示清空(异常路径,本期未使用,先留入口)。
     *
     * <p>record 不可变,本方法返回新实例;{@code createdAt} 保持不变(不视为订单修改),
     * {@code updatedAt} 写为当前时刻。
     */
    public Order withEstimatedDelivery(Instant newEstimated) {
        return new Order(id, userId, items, subtotal, shippingFee, discount, totalAmount, status,
                cancelReason, tracking, refundId, newEstimated, createdAt, Instant.now());
    }

    private void requireTransition(Class<? extends OrderStatus> target) {
        OrderStatus t = switch (target.getSimpleName()) {
            case "Paid"      -> new OrderStatus.Paid();
            case "Shipped"   -> new OrderStatus.Shipped();
            case "Completed" -> new OrderStatus.Completed();
            case "Refunding" -> new OrderStatus.Refunding();
            case "Refunded"  -> new OrderStatus.Refunded();
            default -> throw new IllegalStateException();
        };
        if (!status.canTransitionTo(t)) {
            throw new DomainException("非法状态转移:" + status.code() + " → " + t.code());
        }
    }

    private Order mutate(OrderStatus newStatus, String reason, OrderTracking newTracking, String newRefundId, Instant when) {
        return new Order(id, userId, items, subtotal, shippingFee, discount, totalAmount, newStatus, reason,
                newTracking, newRefundId, estimatedDelivery,
                createdAt, when == null ? Instant.now() : when);
    }
}
