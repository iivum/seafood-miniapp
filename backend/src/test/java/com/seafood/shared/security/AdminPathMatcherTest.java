package com.seafood.shared.security;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * PR review I6 — {@link AdminPathMatcher} 单测,锁定"两 filter 共享"行为契约。
 */
class AdminPathMatcherTest {

    @Test
    void exactPathMatches() {
        assertThat(AdminPathMatcher.isAdminPath("/api/admin")).isTrue();
    }

    @Test
    void subPathsMatch() {
        assertThat(AdminPathMatcher.isAdminPath("/api/admin/dashboard")).isTrue();
        assertThat(AdminPathMatcher.isAdminPath("/api/admin/auth/login")).isTrue();
        assertThat(AdminPathMatcher.isAdminPath("/api/admin/")).isTrue();
    }

    @Test
    void similarPrefixesDoNotMatch() {
        // 关键:近似前缀不应被误判(之前是 PR review #14 修过的旁路)
        assertThat(AdminPathMatcher.isAdminPath("/api/adminalice")).isFalse();
        assertThat(AdminPathMatcher.isAdminPath("/api/adminfoo")).isFalse();
    }

    @Test
    void unrelatedPathsDoNotMatch() {
        assertThat(AdminPathMatcher.isAdminPath("/api/users/me")).isFalse();
        assertThat(AdminPathMatcher.isAdminPath("/api/products")).isFalse();
        assertThat(AdminPathMatcher.isAdminPath("/")).isFalse();
    }

    @Test
    void nullReturnsFalse() {
        assertThat(AdminPathMatcher.isAdminPath(null)).isFalse();
    }
}
