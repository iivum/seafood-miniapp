package com.seafood.bff.admin.dto;

import java.util.List;

/**
 * 路线图 3.6 上传响应(参见 docs/redesign-requirements.md § 5 未决问题 6 OSS/S3 决策)。
 *
 * <p>本迭代写本地磁盘,Sprint 4 切 OSS 时只换实现(UploadService.saveToOss()),
 * 响应 shape 不变。{@code url} 是相对路径(/api/static/uploads/yyyy/mm/uuid.ext),
 * 前端拿到后塞到 Product.imageUrl 字段。
 */
public record UploadResponse(
        List<UploadedFile> files
) {
    public record UploadedFile(
            String url,
            long size,
            String mime
    ) {
    }
}
