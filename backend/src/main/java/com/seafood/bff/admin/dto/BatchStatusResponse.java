package com.seafood.bff.admin.dto;

import java.util.List;

/**
 * 路线图 3.3 批量状态变更响应(参见 4.13 BatchShipResponse 同形)。
 *
 * <p>HTTP 200 + body 含 {@code successCount} / {@code failedCount} / {@code successIds} /
 * {@code failed};部分失败时返业务结果(非 207),UI 提示用户。
 */
public record BatchStatusResponse(
        int total,
        int successCount,
        int failedCount,
        List<String> successIds,
        List<FailedItem> failed
) {
    public record FailedItem(
            String productId,
            String reason
    ) {
    }

    public static BatchStatusResponse of(List<String> success, List<FailedItem> failed) {
        return new BatchStatusResponse(
                success.size() + failed.size(),
                success.size(),
                failed.size(),
                success,
                failed
        );
    }
}
