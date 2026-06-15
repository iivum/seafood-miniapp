package com.seafood.bff.admin.dto;

import com.seafood.product.domain.ProductStatus;

import java.util.List;

/**
 * 路线图 3.3 批量状态变更请求(ad-03 DataTable "批量上架/下架"按钮)。
 *
 * <p>body shape:{@code { "ids": ["p1","p2","p3"], "status": "ACTIVE" }}。
 * 端点契约同 4.13 batchShip:HTTP 200 + {@code BatchStatusResponse{successCount, failedCount, ...}},
 * 部分失败时返业务结果(非 207),UI 提示用户。
 */
public record BatchStatusRequest(
        List<String> ids,
        ProductStatus status
) {
}
