package com.seafood.banner.api.dto;

import com.seafood.banner.domain.Banner;
import com.seafood.banner.domain.BannerStatus;
import com.seafood.banner.domain.BannerTone;

import java.time.Instant;

/** Banner 对外响应。 */
public record BannerResponse(
        String id,
        BannerTone tone,
        String emoji,
        String title,
        String subtitle,
        String targetProductId,
        int sortOrder,
        BannerStatus status,
        Instant createdAt,
        Instant updatedAt
) {
    public static BannerResponse from(Banner b) {
        return new BannerResponse(
                b.id(), b.tone(), b.emoji(), b.title(), b.subtitle(),
                b.targetProductId(), b.sortOrder(), b.status(),
                b.createdAt(), b.updatedAt());
    }
}
