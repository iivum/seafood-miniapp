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

    @Test
    void updateQuantity_replacesQuantityForExistingLine() {
        Cart c = Cart.empty("u1").addItem("p1", 2).updateQuantity("p1", 9);
        assertThat(c.items()).hasSize(1);
        assertThat(c.items().get(0).quantity()).isEqualTo(9);
    }

    @Test
    void updateQuantity_doesNotAddToExistingQuantity() {
        // design D2: PUT 是整数替换,不是累加 —— 拒绝任何把新数量加到旧数量上的实现。
        Cart c = Cart.empty("u1").addItem("p1", 2).updateQuantity("p1", 9);
        assertThat(c.items().get(0).quantity()).isNotEqualTo(11);
        assertThat(c.items().get(0).quantity()).isEqualTo(9);
    }

    @Test
    void updateQuantity_unknownProductId_throws() {
        assertThatThrownBy(() -> Cart.empty("u1").addItem("p1", 1).updateQuantity("p-missing", 3))
                .isInstanceOf(CartItemNotFoundException.class);
    }

    @Test
    void toggleSelected_flipsSelectedForExistingLine() {
        Cart c = Cart.empty("u1").addItem("p1", 1);
        assertThat(c.items().get(0).selected()).isTrue();

        Cart toggledOff = c.toggleSelected("p1");
        assertThat(toggledOff.items().get(0).selected()).isFalse();

        Cart toggledOn = toggledOff.toggleSelected("p1");
        assertThat(toggledOn.items().get(0).selected()).isTrue();
    }

    @Test
    void toggleSelected_unknownProductId_throws() {
        assertThatThrownBy(() -> Cart.empty("u1").addItem("p1", 1).toggleSelected("p-missing"))
                .isInstanceOf(CartItemNotFoundException.class);
    }
}
