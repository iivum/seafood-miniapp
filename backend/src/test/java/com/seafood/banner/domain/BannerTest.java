package com.seafood.banner.domain;

import com.seafood.shared.error.DomainException;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Banner 聚合:构造校验 + 行为方法。 */
class BannerTest {

    private static Banner valid() {
        Instant now = Instant.parse("2026-06-20T00:00:00Z");
        return new Banner("b-1", BannerTone.ACCENT, "🦞", "波龙季 返场",
                "鲜活到岸 · 满 1 只减 30", "p-1", 0, BannerStatus.ACTIVE, now, now);
    }

    @Test
    void construct_valid_defaultsActive() {
        Banner b = valid();
        assertThat(b.status()).isEqualTo(BannerStatus.ACTIVE);
        assertThat(b.title()).isEqualTo("波龙季 返场");
        assertThat(b.targetProductId()).isEqualTo("p-1");
    }

    @Test
    void construct_blankTitle_rejected() {
        Instant now = Instant.now();
        assertThatThrownBy(() -> new Banner("b", BannerTone.ACCENT, "🦞", "  ",
                "sub", null, 0, BannerStatus.ACTIVE, now, now))
                .isInstanceOf(DomainException.class);
    }

    @Test
    void construct_blankSubtitle_rejected() {
        Instant now = Instant.now();
        assertThatThrownBy(() -> new Banner("b", BannerTone.ACCENT, "🦞", "t",
                "", null, 0, BannerStatus.ACTIVE, now, now))
                .isInstanceOf(DomainException.class);
    }

    @Test
    void construct_negativeSortOrder_rejected() {
        Instant now = Instant.now();
        assertThatThrownBy(() -> new Banner("b", BannerTone.ACCENT, "🦞", "t",
                "sub", null, -1, BannerStatus.ACTIVE, now, now))
                .isInstanceOf(DomainException.class);
    }

    @Test
    void construct_nullTone_rejected() {
        Instant now = Instant.now();
        assertThatThrownBy(() -> new Banner("b", null, "🦞", "t",
                "sub", null, 0, BannerStatus.ACTIVE, now, now))
                .isInstanceOf(DomainException.class);
    }

    @Test
    void construct_nullableTargetProductId_allowed() {
        Instant now = Instant.now();
        Banner b = new Banner("b", BannerTone.SOFT, "🐟", "t", "sub",
                null, 3, BannerStatus.ACTIVE, now, now);
        assertThat(b.targetProductId()).isNull();
    }

    @Test
    void deactivate_setsInactive_andBumpsUpdatedAt() {
        Banner b = valid();
        Banner off = b.deactivate();
        assertThat(off.status()).isEqualTo(BannerStatus.INACTIVE);
        assertThat(off.id()).isEqualTo(b.id());
        assertThat(off.createdAt()).isEqualTo(b.createdAt());
    }

    @Test
    void activate_setsActive() {
        Banner off = valid().deactivate();
        assertThat(off.activate().status()).isEqualTo(BannerStatus.ACTIVE);
    }

    @Test
    void updateContent_revalidates_andReturnsNewRecord() {
        Banner b = valid();
        Banner updated = b.updateContent(BannerTone.SOFT, "🦀", "大闸蟹 旺季",
                "公 4 两 · 整 8 只装", "p-9", 5);
        assertThat(updated.tone()).isEqualTo(BannerTone.SOFT);
        assertThat(updated.title()).isEqualTo("大闸蟹 旺季");
        assertThat(updated.sortOrder()).isEqualTo(5);
        assertThat(updated.targetProductId()).isEqualTo("p-9");
        assertThat(updated.status()).isEqualTo(b.status());
    }

    @Test
    void updateContent_blankTitle_rejected() {
        Banner b = valid();
        assertThatThrownBy(() -> b.updateContent(BannerTone.SOFT, "🦀", " ", "sub", null, 1))
                .isInstanceOf(DomainException.class);
    }
}
