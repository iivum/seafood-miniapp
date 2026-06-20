package com.seafood.banner.domain;

/**
 * Banner 状态。{@code ACTIVE} = 启用(出现在公共 {@code GET /api/banners}),
 * {@code INACTIVE} = 停用(仅 admin {@code GET /api/banners/all} 可见)。
 */
public enum BannerStatus {
    ACTIVE,
    INACTIVE
}
