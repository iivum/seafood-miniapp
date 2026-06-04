package com.seafood.order.domain;

import com.seafood.shared.error.DomainException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CartTest {

    @Test
    void addItem_mergesDuplicateProduct() {
        Cart c = Cart.empty("u1").addItem("p1", 2).addItem("p1", 3);
        assertThat(c.items()).hasSize(1);
        assertThat(c.items().get(0).quantity()).isEqualTo(5);
    }

    @Test
    void addItem_rejectsNonPositiveQuantity() {
        assertThatThrownBy(() -> Cart.empty("u1").addItem("p1", 0))
                .isInstanceOf(DomainException.class);
    }

    @Test
    void removeItem_dropsMatching() {
        Cart c = Cart.empty("u1").addItem("p1", 1).addItem("p2", 2).removeItem("p1");
        assertThat(c.items()).extracting(CartItem::productId).containsExactly("p2");
    }

    @Test
    void clear_emptiesList() {
        Cart c = Cart.empty("u1").addItem("p1", 1).clear();
        assertThat(c.items()).isEmpty();
    }

    @Test
    void requireNonEmptySelected_throwsOnEmpty() {
        assertThatThrownBy(() -> Cart.empty("u1").requireNonEmptySelected())
                .isInstanceOf(DomainException.class)
                .hasMessageContaining("购物车为空");
    }
}
