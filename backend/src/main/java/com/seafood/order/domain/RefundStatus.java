package com.seafood.order.domain;

import com.seafood.shared.error.DomainException;

/**
 * 退款单状态机(参见 specs/admin-batch-operations §Refund lifecycle + design §6.3)。
 *
 * <p>合法转移(其它一律抛 DomainException):
 * <pre>
 *   REQUESTED → APPROVED   (admin 同意退款,Sprint 3 4.8)
 *             → REJECTED   (admin 拒绝退款,Order 回退到 COMPLETED)
 * </pre>
 *
 * <p>本迭代(4.6)只引入 {@code Refund} 聚合根 + 仓储 + 状态机,不动 Order 流转;
 * Sprint 3 task 4.7 实现「创建 Refund + Order 转 REFUNDING」端点;4.8 实现 admin 同意/拒绝
 * 端点(更新 Refund.status + Order 流转到 REFUNDED / COMPLETED)。
 *
 * <p>不允许循环:APPROVED / REJECTED 都是终态(同 Order 终态语义)。
 */
public sealed interface RefundStatus
        permits RefundStatus.Requested, RefundStatus.Approved, RefundStatus.Rejected {

    String code();

    record Requested() implements RefundStatus { public String code() { return "REQUESTED"; } }
    record Approved()  implements RefundStatus { public String code() { return "APPROVED"; } }
    record Rejected()  implements RefundStatus { public String code() { return "REJECTED"; } }

    static RefundStatus of(String code) {
        return switch (code) {
            case "REQUESTED" -> new Requested();
            case "APPROVED"  -> new Approved();
            case "REJECTED"  -> new Rejected();
            default -> throw new DomainException("未知退款状态:" + code);
        };
    }

    default boolean isTerminal() {
        return this instanceof Approved || this instanceof Rejected;
    }
}
