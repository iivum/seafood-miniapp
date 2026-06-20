package com.seafood.banner.domain;

import com.seafood.shared.error.DomainException;

import java.time.Instant;

/**
 * Banner 聚合根(参见 specs/banner-management)。
 *
 * <p>设计取舍(对齐 product 模块):
 * <ul>
 *   <li>Java record 表达不可变状态;变更走命名行为方法返回新 record(避免 setter 漂移)</li>
 *   <li>不变量(title/subtitle 非空、tone/emoji 非空、sortOrder &ge; 0、status 非空)构造期校验</li>
 *   <li>{@code targetProductId} 可空:空 = 纯展示 banner;非空 = 点击跳商品详情
 *       (存在性由 {@code BannerService} 经 ProductService 跨模块校验,不在聚合内)</li>
 * </ul>
 */
public record Banner(
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

    public Banner {
        if (tone == null) {
            throw new DomainException("banner 色调不能为空");
        }
        if (emoji == null || emoji.isBlank()) {
            throw new DomainException("banner emoji 不能为空");
        }
        if (title == null || title.isBlank()) {
            throw new DomainException("banner 标题不能为空");
        }
        if (subtitle == null || subtitle.isBlank()) {
            throw new DomainException("banner 副标题不能为空");
        }
        if (sortOrder < 0) {
            throw new DomainException("banner 排序值不能为负");
        }
        if (status == null) {
            throw new DomainException("banner 状态不能为空");
        }
    }

    /** 停用:返回新 record,status = INACTIVE。 */
    public Banner deactivate() {
        return new Banner(id, tone, emoji, title, subtitle, targetProductId,
                sortOrder, BannerStatus.INACTIVE, createdAt, Instant.now());
    }

    /** 启用:返回新 record,status = ACTIVE。 */
    public Banner activate() {
        return new Banner(id, tone, emoji, title, subtitle, targetProductId,
                sortOrder, BannerStatus.ACTIVE, createdAt, Instant.now());
    }

    /** 更新展示内容(不动 id/status/createdAt);校验经紧凑构造器复用。 */
    public Banner updateContent(BannerTone newTone, String newEmoji, String newTitle,
                                String newSubtitle, String newTargetProductId, int newSortOrder) {
        return new Banner(id, newTone, newEmoji, newTitle, newSubtitle, newTargetProductId,
                newSortOrder, status, createdAt, Instant.now());
    }
}
