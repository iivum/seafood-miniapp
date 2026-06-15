package com.seafood.bff.admin.dto;

import java.util.List;

/**
 * 批量发货响应(参见 design.md §5.1 + specs/admin-batch-operations §Bulk ship)。
 *
 * <p>路线图 4.13 — admin 端"批量发货"按钮点完调后,UI 用此结果刷新列表:
 * <ul>
 *   <li>{@code successIds} — 已成功转 SHIPPED 的订单 ID 列表(可乐观更新本地状态)</li>
 *   <li>{@code failed} — 失败明细(orderId + reason),UI 弹 toast 逐条显示并红框标记行</li>
 *   <li>{@code total / successCount / failedCount} — 统计,UI 顶栏显示</li>
 * </ul>
 *
 * <p>设计:**逐单处理 + 失败跳过**。任一单失败不阻塞其它单(类似 Stripe / Shopify batch API),
 * 比"全有或全无"更友好 — admin 不必因 1 单异常重发整批。但 successIds 一定是已落库的
 * (持久化后再 add),不会回滚(failure 是预校验拦截的,不是落库后失败)。
 */
public record BatchShipResponse(
        List<String> successIds,
        List<FailedItem> failed,
        int total,
        int successCount,
        int failedCount
) {
    public record FailedItem(String orderId, String reason) {}

    public static BatchShipResponse of(List<String> success, List<FailedItem> failed) {
        return new BatchShipResponse(
                success,
                failed,
                success.size() + failed.size(),
                success.size(),
                failed.size());
    }
}
