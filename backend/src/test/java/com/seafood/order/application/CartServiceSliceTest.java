package com.seafood.order.application;

import com.seafood.order.api.dto.CartItemRequest;
import com.seafood.order.api.dto.CartResponse;
import com.seafood.order.domain.CartItem;
import com.seafood.order.infra.CartDocument;
import com.seafood.order.infra.CartRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CartServiceSliceTest {

    @Mock private CartRepository cartRepository;
    private CartService cartService;

    @BeforeEach
    void setUp() {
        cartService = new CartService(cartRepository);
    }

    private static CartDocument doc(String userId, List<CartItem> items) {
        CartDocument d = new CartDocument();
        d.setUserId(userId);
        d.setItems(items);
        d.setUpdatedAt(Instant.parse("2026-06-19T00:00:00Z"));
        return d;
    }

    @Test
    void get_existingUser_returnsCart() {
        when(cartRepository.findById("u-1"))
            .thenReturn(Optional.of(doc("u-1", List.of())));

        CartResponse resp = cartService.get("u-1");

        assertThat(resp.userId()).isEqualTo("u-1");
    }

    @Test
    void addItem_emptyCart_savesWithNewItem() {
        when(cartRepository.findById("u-1"))
            .thenReturn(Optional.of(doc("u-1", List.of())));

        cartService.addItem("u-1", new CartItemRequest("p-1", 2));

        ArgumentCaptor<CartDocument> captor = ArgumentCaptor.forClass(CartDocument.class);
        verify(cartRepository).save(captor.capture());
        assertThat(captor.getValue().getItems()).hasSize(1);
        assertThat(captor.getValue().getItems().get(0).productId()).isEqualTo("p-1");
        assertThat(captor.getValue().getItems().get(0).quantity()).isEqualTo(2);
    }

    @Test
    void removeItem_existingItem_savesWithoutIt() {
        when(cartRepository.findById("u-1")).thenReturn(Optional.of(doc("u-1", List.of(
            new CartItem("p-1", 1, true, Instant.parse("2026-06-19T00:00:00Z")),
            new CartItem("p-2", 1, true, Instant.parse("2026-06-19T00:00:00Z"))))));

        cartService.removeItem("u-1", "p-1");

        ArgumentCaptor<CartDocument> captor = ArgumentCaptor.forClass(CartDocument.class);
        verify(cartRepository).save(captor.capture());
        assertThat(captor.getValue().getItems()).hasSize(1);
        assertThat(captor.getValue().getItems().get(0).productId()).isEqualTo("p-2");
    }

    @Test
    void clear_deletesCartById() {
        cartService.clear("u-1");

        verify(cartRepository).deleteById("u-1");
    }
}
