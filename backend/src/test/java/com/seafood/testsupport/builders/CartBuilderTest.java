package com.seafood.testsupport.builders;

import com.seafood.order.domain.Cart;
import com.seafood.order.domain.CartItem;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class CartBuilderTest {

    @Test
    void defaultBuild_returnsEmptyCart() {
        Cart c = CartBuilder.aCart().build();
        assertThat(c.userId()).isEqualTo("u-test");
        assertThat(c.items()).isEmpty();
    }

    @Test
    void withItems_addsItems() {
        CartItem item = new CartItem("p-1", 2, true,
            Instant.parse("2026-06-01T00:00:00Z"));
        Cart c = CartBuilder.aCart().withItems(java.util.List.of(item)).build();
        assertThat(c.items()).hasSize(1);
    }

    @Test
    void withUserId_overridesUserId() {
        Cart c = CartBuilder.aCart().withUserId("u-custom").build();
        assertThat(c.userId()).isEqualTo("u-custom");
    }
}
