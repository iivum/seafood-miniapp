package com.seafood.banner.domain;

import com.seafood.shared.error.DomainException;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.constraints.AlphaChars;
import net.jqwik.api.constraints.IntRange;
import net.jqwik.api.constraints.StringLength;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Banner 紧凑构造器校验 property(对齐 product C4 风格)。
 *
 * <p>∀ 合法字段 → 构造成功;∀ 负 sortOrder → DomainException。随机样本逼边界。
 */
class BannerConstructionProperties {

    private static final Instant NOW = Instant.parse("2026-06-20T00:00:00Z");

    /** ∀ 合法字段(title/subtitle/emoji 非空、sortOrder≥0)→ 构造成功,status 不变。 */
    @Property
    void validFields_constructsSuccessfully(
            @ForAll @AlphaChars @StringLength(min = 1, max = 60) String title,
            @ForAll @AlphaChars @StringLength(min = 1, max = 80) String subtitle,
            @ForAll @IntRange(min = 0, max = 9999) int sortOrder) {
        Banner b = new Banner("b-1", BannerTone.ACCENT, "🦞", title, subtitle,
                null, sortOrder, BannerStatus.ACTIVE, NOW, NOW);
        assertThat(b.title()).isEqualTo(title);
        assertThat(b.sortOrder()).isEqualTo(sortOrder);
        assertThat(b.status()).isEqualTo(BannerStatus.ACTIVE);
    }

    /** ∀ 负 sortOrder → DomainException。 */
    @Property
    void negativeSortOrder_throws(
            @ForAll @IntRange(min = -9999, max = -1) int sortOrder) {
        assertThatThrownBy(() -> new Banner("b", BannerTone.SOFT, "🐟", "t", "sub",
                null, sortOrder, BannerStatus.ACTIVE, NOW, NOW))
                .isInstanceOf(DomainException.class);
    }

    /** ∀ 空白 title(长度内但全空格)→ DomainException。 */
    @Property
    void blankTitle_throws(
            @ForAll @StringLength(min = 1, max = 10) String spaces) {
        String blank = " ".repeat(spaces.length());
        assertThatThrownBy(() -> new Banner("b", BannerTone.ACCENT, "🦞", blank, "sub",
                null, 0, BannerStatus.ACTIVE, NOW, NOW))
                .isInstanceOf(DomainException.class);
    }
}
