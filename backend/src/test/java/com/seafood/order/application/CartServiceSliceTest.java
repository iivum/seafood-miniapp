package com.seafood.order.application;

import com.seafood.order.api.dto.CartItemRequest;
import com.seafood.order.api.dto.CartLineItemResponse;
import com.seafood.order.api.dto.CartResponse;
import com.seafood.order.domain.CartItem;
import com.seafood.order.infra.CartDocument;
import com.seafood.order.infra.CartRepository;
import com.seafood.product.api.dto.ProductResponse;
import com.seafood.product.application.ProductService;
import com.seafood.product.domain.ProductStatus;
import com.seafood.shared.error.NotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CartServiceSliceTest {

    private static final Instant T = Instant.parse("2026-06-19T00:00:00Z");

    @Mock private CartRepository cartRepository;
    @Mock private ProductService productService;
    private CartService cartService;

    @BeforeEach
    void setUp() {
        cartService = new CartService(cartRepository, productService);
    }

    private static CartDocument doc(String userId, List<CartItem> items) {
        CartDocument d = new CartDocument();
        d.setUserId(userId);
        d.setItems(items);
        d.setUpdatedAt(T);
        return d;
    }

    private static ProductResponse product(String id, String name, BigDecimal price, String imageUrl) {
        return new ProductResponse(id, name, "desc", price, 10, "鱼类", imageUrl, ProductStatus.ACTIVE, T, T);
    }

    @Test
    void get_existingUser_returnsCart() {
        when(cartRepository.findById("u-1"))
            .thenReturn(Optional.of(doc("u-1", List.of())));

        CartResponse resp = cartService.get("u-1");

        assertThat(resp.userId()).isEqualTo("u-1");
    }

    @Test
    void get_existingProduct_enrichesLineItemFromProductService() {
        when(cartRepository.findById("u-1")).thenReturn(Optional.of(doc("u-1", List.of(
            new CartItem("p-1", 2, true, T)))));
        when(productService.get("p-1"))
            .thenReturn(product("p-1", "大闸蟹", new BigDecimal("99.00"), "https://cdn/p1.jpg"));

        CartResponse resp = cartService.get("u-1");

        assertThat(resp.items()).hasSize(1);
        CartLineItemResponse line = resp.items().get(0);
        assertThat(line.productId()).isEqualTo("p-1");
        assertThat(line.productName()).isEqualTo("大闸蟹");
        assertThat(line.unitPrice()).isEqualByComparingTo("99.00");
        assertThat(line.imageUrl()).isEqualTo("https://cdn/p1.jpg");
        assertThat(line.quantity()).isEqualTo(2);
        assertThat(line.selected()).isTrue();
        assertThat(line.available()).isTrue();
    }

    @Test
    void get_deletedProduct_degradesLineItemInsteadOfThrowing() {
        when(cartRepository.findById("u-1")).thenReturn(Optional.of(doc("u-1", List.of(
            new CartItem("p-gone", 1, true, T)))));
        when(productService.get("p-gone")).thenThrow(new NotFoundException("商品不存在:p-gone"));

        assertThatCode(() -> cartService.get("u-1")).doesNotThrowAnyException();

        CartResponse resp = cartService.get("u-1");
        CartLineItemResponse line = resp.items().get(0);
        assertThat(line.productId()).isEqualTo("p-gone");
        assertThat(line.available()).isFalse();
        assertThat(line.unitPrice()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(line.productName()).isNotBlank();
    }

    @Test
    void get_mixedAvailability_onlyDegradesTheFailingLine() {
        when(cartRepository.findById("u-1")).thenReturn(Optional.of(doc("u-1", List.of(
            new CartItem("p-1", 1, true, T),
            new CartItem("p-gone", 1, true, T)))));
        when(productService.get("p-1"))
            .thenReturn(product("p-1", "大闸蟹", new BigDecimal("99.00"), "https://cdn/p1.jpg"));
        when(productService.get("p-gone")).thenThrow(new NotFoundException("商品不存在:p-gone"));

        CartResponse resp = cartService.get("u-1");

        assertThat(resp.items()).hasSize(2);
        assertThat(resp.items().get(0).available()).isTrue();
        assertThat(resp.items().get(1).available()).isFalse();
    }

    @Test
    void addItem_emptyCart_savesWithNewItem() {
        when(cartRepository.findById("u-1"))
            .thenReturn(Optional.of(doc("u-1", List.of())));
        when(productService.get("p-1"))
            .thenReturn(product("p-1", "大闸蟹", new BigDecimal("99.00"), "https://cdn/p1.jpg"));

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
            new CartItem("p-1", 1, true, T),
            new CartItem("p-2", 1, true, T)))));
        when(productService.get(eq("p-2")))
            .thenReturn(product("p-2", "带鱼", new BigDecimal("39.00"), "https://cdn/p2.jpg"));

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

    @Test
    void updateQuantity_existingLine_replacesQuantityAndSaves() {
        when(cartRepository.findById("u-1")).thenReturn(Optional.of(doc("u-1", List.of(
            new CartItem("p-1", 2, true, T)))));
        when(productService.get("p-1"))
            .thenReturn(product("p-1", "大闸蟹", new BigDecimal("99.00"), "https://cdn/p1.jpg"));

        cartService.updateQuantity("u-1", "p-1", 9);

        ArgumentCaptor<CartDocument> captor = ArgumentCaptor.forClass(CartDocument.class);
        verify(cartRepository).save(captor.capture());
        assertThat(captor.getValue().getItems()).hasSize(1);
        assertThat(captor.getValue().getItems().get(0).quantity()).isEqualTo(9);
    }

    @Test
    void updateQuantity_unknownProductId_throwsNotFoundException() {
        when(cartRepository.findById("u-1")).thenReturn(Optional.of(doc("u-1", List.of(
            new CartItem("p-1", 2, true, T)))));

        assertThatThrownBy(
                () -> cartService.updateQuantity("u-1", "p-missing", 5))
            .isInstanceOf(NotFoundException.class);
    }

    @Test
    void toggleSelected_existingLine_flipsSelectedAndSaves() {
        when(cartRepository.findById("u-1")).thenReturn(Optional.of(doc("u-1", List.of(
            new CartItem("p-1", 2, true, T)))));
        when(productService.get("p-1"))
            .thenReturn(product("p-1", "大闸蟹", new BigDecimal("99.00"), "https://cdn/p1.jpg"));

        cartService.toggleSelected("u-1", "p-1");

        ArgumentCaptor<CartDocument> captor = ArgumentCaptor.forClass(CartDocument.class);
        verify(cartRepository).save(captor.capture());
        assertThat(captor.getValue().getItems().get(0).selected()).isFalse();
    }

    @Test
    void toggleSelected_unknownProductId_throwsNotFoundException() {
        when(cartRepository.findById("u-1")).thenReturn(Optional.of(doc("u-1", List.of(
            new CartItem("p-1", 2, true, T)))));

        assertThatThrownBy(
                () -> cartService.toggleSelected("u-1", "p-missing"))
            .isInstanceOf(NotFoundException.class);
    }
}
