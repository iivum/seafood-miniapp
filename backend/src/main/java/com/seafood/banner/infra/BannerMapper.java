package com.seafood.banner.infra;

import com.seafood.banner.domain.Banner;

import java.time.Instant;

/** BannerDocument ↔ Banner 域对象映射(对齐 ProductMapper)。 */
public final class BannerMapper {

    private BannerMapper() {}

    public static Banner toDomain(BannerDocument d) {
        if (d == null) {
            return null;
        }
        return new Banner(
                d.getId(),
                d.getTone(),
                d.getEmoji(),
                d.getTitle(),
                d.getSubtitle(),
                d.getTargetProductId(),
                d.getSortOrder(),
                d.getStatus(),
                d.getCreatedAt(),
                d.getUpdatedAt());
    }

    public static BannerDocument toDocument(Banner b) {
        BannerDocument d = new BannerDocument();
        d.setId(b.id());
        d.setTone(b.tone());
        d.setEmoji(b.emoji());
        d.setTitle(b.title());
        d.setSubtitle(b.subtitle());
        d.setTargetProductId(b.targetProductId());
        d.setSortOrder(b.sortOrder());
        d.setStatus(b.status());
        d.setCreatedAt(b.createdAt() == null ? Instant.now() : b.createdAt());
        d.setUpdatedAt(b.updatedAt() == null ? Instant.now() : b.updatedAt());
        return d;
    }
}
