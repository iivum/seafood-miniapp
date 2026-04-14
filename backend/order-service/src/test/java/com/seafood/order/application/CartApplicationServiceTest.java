package com.seafood.order.application;

import com.seafood.order.domain.model.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Test class for CartApplicationService
 * Tests the application layer service operations for cart management
 */
@ExtendWith(MockitoExtension.class)
class CartApplicationServiceTest {

    @Mock
    private CartRepository cartRepository;

    @InjectMocks
    private CartApplicationService cartApplicationService;

    private Cart testCart;
    private CartItem testItem;
    private CartItem anotherItem;

    @BeforeEach
    void setUp() {
        testCart = new Cart("user-123");
        testItem = new CartItem("product-1", "Fresh Salmon", 29.99, 2, "https://example.com/salmon.jpg");
        anotherItem = new CartItem("product-2", "Fresh Tuna", 39.99, 1, "https://example.com/tuna.jpg");
    }

    @Test
    void shouldGetOrCreateCartSuccessfully() {
        // Arrange
        when(cartRepository.findByUserId("user-123")).thenReturn(Optional.empty());
        when(cartRepository.save(any(Cart.class))).thenAnswer(invocation -> invocation.getArgument(0));

        // Act
        Cart result = cartApplicationService.getOrCreateCart("user-123");

        // Assert
        assertNotNull(result);
        assertEquals("user-123", result.getUserId());
        verify(cartRepository, times(1)).save(any(Cart.class));
    }

    @Test
    void shouldGetExistingCart() {
        // Arrange
        testCart.addItem(testItem);
        when(cartRepository.findByUserId("user-123")).thenReturn(Optional.of(testCart));

        // Act
        Cart result = cartApplicationService.getOrCreateCart("user-123");

        // Assert
        assertNotNull(result);
        assertEquals(testCart.getId(), result.getId());
        assertEquals(1, result.getItems().size());
        verify(cartRepository, never()).save(any(Cart.class));
    }

    @Test
    void shouldAddItemToCartSuccessfully() {
        // Arrange
        testCart.addItem(testItem);
        when(cartRepository.findByUserId("user-123")).thenReturn(Optional.of(testCart));
        when(cartRepository.save(any(Cart.class))).thenAnswer(invocation -> invocation.getArgument(0));

        // Act
        Cart result = cartApplicationService.addItemToCart("user-123", "product-2", 1, "Fresh Tuna", 39.99, "https://example.com/tuna.jpg");

        // Assert
        assertNotNull(result);
        assertEquals(2, result.getItems().size());
        assertEquals(99.97, result.getTotalPrice());
        verify(cartRepository, times(1)).save(any(Cart.class));
    }

    @Test
    void shouldUpdateItemQuantitySuccessfully() {
        // Arrange
        testCart.addItem(testItem);
        when(cartRepository.findByUserId("user-123")).thenReturn(Optional.of(testCart));
        when(cartRepository.save(any(Cart.class))).thenAnswer(invocation -> invocation.getArgument(0));

        // Act
        Cart result = cartApplicationService.updateItemQuantity("user-123", "product-1", 5);

        // Assert
        assertNotNull(result);
        assertEquals(5, result.getItems().get(0).getQuantity());
        assertEquals(149.95, result.getTotalPrice());
        verify(cartRepository, times(1)).save(any(Cart.class));
    }

    @Test
    void shouldRemoveItemFromCartSuccessfully() {
        // Arrange
        testCart.addItem(testItem);
        testCart.addItem(anotherItem);
        when(cartRepository.findByUserId("user-123")).thenReturn(Optional.of(testCart));
        when(cartRepository.save(any(Cart.class))).thenAnswer(invocation -> invocation.getArgument(0));

        // Act
        Cart result = cartApplicationService.removeItemFromCart("user-123", "product-1");

        // Assert
        assertNotNull(result);
        assertEquals(1, result.getItems().size());
        assertEquals(39.99, result.getTotalPrice());
        verify(cartRepository, times(1)).save(any(Cart.class));
    }

    @Test
    void shouldClearCartSuccessfully() {
        // Arrange
        testCart.addItem(testItem);
        testCart.addItem(anotherItem);
        when(cartRepository.findByUserId("user-123")).thenReturn(Optional.of(testCart));
        when(cartRepository.save(any(Cart.class))).thenAnswer(invocation -> invocation.getArgument(0));

        // Act
        Cart result = cartApplicationService.clearCart("user-123");

        // Assert
        assertNotNull(result);
        assertTrue(result.getItems().isEmpty());
        assertEquals(0, result.getTotalPrice());
        assertEquals(0, result.getTotalItems());
        verify(cartRepository, times(1)).save(any(Cart.class));
    }

    @Test
    void shouldToggleItemSelectionSuccessfully() {
        // Arrange
        testCart.addItem(testItem);
        when(cartRepository.findByUserId("user-123")).thenReturn(Optional.of(testCart));
        when(cartRepository.save(any(Cart.class))).thenAnswer(invocation -> invocation.getArgument(0));

        // Act - Select item
        Cart result = cartApplicationService.toggleItemSelection("user-123", "product-1");

        // Assert - Item should be selected
        assertTrue(result.getSelectedItems().contains("product-1"));

        // Act - Toggle again (deselect)
        result = cartApplicationService.toggleItemSelection("user-123", "product-1");

        // Assert - Item should not be selected
        assertFalse(result.getSelectedItems().contains("product-1"));
    }

    @Test
    void shouldGetCartTotalPriceSuccessfully() {
        // Arrange
        testCart.addItem(testItem);
        testCart.addItem(anotherItem);
        when(cartRepository.findByUserId("user-123")).thenReturn(Optional.of(testCart));

        // Act
        double totalPrice = cartApplicationService.getCartTotalPrice("user-123");

        // Assert
        assertEquals(99.97, totalPrice);
    }

    @Test
    void shouldGetCartTotalItemsSuccessfully() {
        // Arrange
        testCart.addItem(testItem);
        testCart.addItem(anotherItem);
        when(cartRepository.findByUserId("user-123")).thenReturn(Optional.of(testCart));

        // Act
        int totalItems = cartApplicationService.getCartTotalItems("user-123");

        // Assert
        assertEquals(3, totalItems);
    }

    @Test
    void shouldSelectAllItemsSuccessfully() {
        // Arrange
        testCart.addItem(testItem);
        testCart.addItem(anotherItem);
        when(cartRepository.findByUserId("user-123")).thenReturn(Optional.of(testCart));
        when(cartRepository.save(any(Cart.class))).thenAnswer(invocation -> invocation.getArgument(0));

        // Act
        Cart result = cartApplicationService.selectAllItems("user-123");

        // Assert
        assertEquals(2, result.getSelectedItems().size());
        assertTrue(result.getSelectedItems().contains("product-1"));
        assertTrue(result.getSelectedItems().contains("product-2"));
    }

    @Test
    void shouldDeselectAllItemsSuccessfully() {
        // Arrange
        testCart.addItem(testItem);
        testCart.addItem(anotherItem);
        testCart.selectAllItems();
        when(cartRepository.findByUserId("user-123")).thenReturn(Optional.of(testCart));
        when(cartRepository.save(any(Cart.class))).thenAnswer(invocation -> invocation.getArgument(0));

        // Act
        Cart result = cartApplicationService.deselectAllItems("user-123");

        // Assert
        assertTrue(result.getSelectedItems().isEmpty());
    }

    @Test
    void shouldHandleCartNotFoundWhenGettingTotalPrice() {
        // Arrange
        when(cartRepository.findByUserId("user-123")).thenReturn(Optional.empty());

        // Act & Assert
        assertThrows(IllegalArgumentException.class, () -> {
            cartApplicationService.getCartTotalPrice("user-123");
        });
    }

    @Test
    void shouldHandleInvalidUserId() {
        // Act & Assert
        assertThrows(IllegalArgumentException.class, () -> {
            cartApplicationService.getOrCreateCart(null);
        });

        assertThrows(IllegalArgumentException.class, () -> {
            cartApplicationService.getOrCreateCart("");
        });
    }

    @Test
    void shouldHandleInvalidProductIdWhenAddingItem() {
        // Act & Assert
        assertThrows(IllegalArgumentException.class, () -> {
            cartApplicationService.addItemToCart("user-123", null, 1, "Test", 10.0, "http://example.com");
        });

        assertThrows(IllegalArgumentException.class, () -> {
            cartApplicationService.addItemToCart("user-123", "", 1, "Test", 10.0, "http://example.com");
        });
    }

    @Test
    void shouldHandleInvalidQuantityWhenAddingItem() {
        // Act & Assert
        assertThrows(IllegalArgumentException.class, () -> {
            cartApplicationService.addItemToCart("user-123", "product-1", 0, "Test", 10.0, "http://example.com");
        });

        assertThrows(IllegalArgumentException.class, () -> {
            cartApplicationService.addItemToCart("user-123", "product-1", -1, "Test", 10.0, "http://example.com");
        });
    }
}