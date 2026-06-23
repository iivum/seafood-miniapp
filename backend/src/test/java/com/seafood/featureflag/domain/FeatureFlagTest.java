package com.seafood.featureflag.domain;

import com.seafood.shared.error.DomainException;
import org.junit.jupiter.api.Test;
import java.time.Instant;
import java.util.List;
import static org.assertj.core.api.Assertions.*;

class FeatureFlagTest {

    private FeatureFlag flag(boolean enabled, int pct, List<String> segments) {
        return new FeatureFlag("test-flag", enabled, pct, segments, null, "desc", "admin",
                Instant.now(), Instant.now());
    }

    @Test void isEnabled_returnsFalse_whenDisabled() {
        assertThat(flag(false, 100, List.of()).isEnabled("user1")).isFalse();
    }

    @Test void isEnabled_returnsTrue_whenInWhitelist() {
        assertThat(flag(true, 0, List.of("user1")).isEnabled("user1")).isTrue();
    }

    @Test void isEnabled_rollout_deterministic() {
        var f = flag(true, 50, List.of());
        boolean first = f.isEnabled("user-abc");
        for (int i = 0; i < 5; i++) assertThat(f.isEnabled("user-abc")).isEqualTo(first);
    }

    @Test void isEnabled_rollout_percentage0_alwaysFalse() {
        var f = flag(true, 0, List.of());
        assertThat(f.isEnabled("user1")).isFalse();
        assertThat(f.isEnabled("user2")).isFalse();
    }

    @Test void isEnabled_rollout_percentage100_alwaysTrue() {
        var f = flag(true, 100, List.of());
        assertThat(f.isEnabled("user1")).isTrue();
        assertThat(f.isEnabled("user2")).isTrue();
    }

    @Test void isEnabled_returnsTrue_whenNullUserId_andPercentage100() {
        assertThat(flag(true, 100, List.of()).isEnabled(null)).isTrue();
    }

    @Test void isExpired_returnsTrue_whenExpiresAtInPast() {
        var f = new FeatureFlag("k", true, 100, List.of(),
                Instant.now().minusSeconds(1), "", "admin", Instant.now(), Instant.now());
        assertThat(f.isEnabled("user1")).isFalse();
    }

    @Test void constructor_rejectsNullFlagKey() {
        assertThatThrownBy(() -> new FeatureFlag(null, true, 50, List.of(), null, "", "admin",
                Instant.now(), Instant.now()))
            .isInstanceOf(DomainException.class);
    }

    @Test void constructor_rejectsBlankFlagKey() {
        assertThatThrownBy(() -> new FeatureFlag("", true, 50, List.of(), null, "", "admin",
                Instant.now(), Instant.now()))
            .isInstanceOf(DomainException.class);
    }

    @Test void constructor_rejectsPercentageOutOfRange() {
        assertThatThrownBy(() -> new FeatureFlag("k", true, -1, List.of(), null, "", "admin",
                Instant.now(), Instant.now()))
            .isInstanceOf(DomainException.class);
        assertThatThrownBy(() -> new FeatureFlag("k", true, 101, List.of(), null, "", "admin",
                Instant.now(), Instant.now()))
            .isInstanceOf(DomainException.class);
    }
}
