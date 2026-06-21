package com.seafood.banner.api.dto;

import com.seafood.banner.domain.BannerTone;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

/**
 * Banner 创建/更新入参。{@code active} 映射 status(true=ACTIVE / false=INACTIVE);
 * {@code targetProductId} 可空(空=纯展示 banner)。
 */
public record BannerRequest(
        @NotNull BannerTone tone,
        @NotBlank String emoji,
        @NotBlank String title,
        @NotBlank String subtitle,
        String targetProductId,
        @PositiveOrZero int sortOrder,
        boolean active
) {
}
