package com.seafood.order.domain.model;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Test class for Cart aggregate root
 * Follows TDD approach to define cart behavior before implementation
 */
class CartTest {

    private Cart cart;
    private CartItem cartItem;

    @BeforeEach
    void setUp() {
        cart = new Cart("user-123");
        cartItem = new CartItem("product-1", "Fresh Salmon", 29.99, 2, "https://example.com/salmon.jpg");
    }

    @Test
    void shouldCreateCartSuccessfully() {
        // Assert
        assertNotNull(cart);
        assertEquals("user-123", cart.getUserId());
        assertTrue(cart.getItems().isEmpty());
        assertEquals(0, cart.getTotalPrice());
        assertEquals(0, cart.getTotalItems());
    }

    @Test
    void shouldAddItemToCartSuccessfully() {
        // Act
        cart.addItem(cartItem);

        // Assert
        assertEquals(1, cart.getItems().size());
        assertEquals(cartItem, cart.getItems().get(0));
        assertEquals(59.98, cart.getTotalPrice());
        assertEquals(2, cart.getTotalItems());
    }

    @Test
    void shouldAddMultipleItemsToCart() {
        // Arrange
        CartItem anotherItem = new CartItem("product-2", "Fresh Tuna", 39.99, 1, "https://example.com/tuna.jpg");

        // Act
        cart.addItem(cartItem);
        cart.addItem(anotherItem);

        // Assert
        assertEquals(2, cart.getItems().size());
        assertEquals(99.97, cart.getTotalPrice());
        assertEquals(3, cart.getTotalItems());
    }

    @Test
    void shouldUpdateItemQuantitySuccessfully() {
        // Arrange
        cart.addItem(cartItem);

        // Act
        cart.updateItemQuantity("product-1", 5);

        // Assert
        assertEquals(1, cart.getItems().size());
        assertEquals(5, cart.getItems().get(0).getQuantity());
        assertEquals(149.95, cart.getTotalPrice());
        assertEquals(5, cart.getTotalItems());
    }

    @Test
    void shouldRemoveItemFromCartSuccessfully() {
        // Arrange
        cart.addItem(cartItem);

        // Act
        cart.removeItem("product-1");

        // Assert
        assertTrue(cart.getItems().isEmpty());
        assertEquals(0, cart.getTotalPrice());
        assertEquals(0, cart.getTotalItems());
    }

    @Test
    void shouldClearCartSuccessfully() {
        // Arrange
        cart.addItem(cartItem);
        CartItem anotherItem = new CartItem("product-2", "Fresh Tuna", 39.99, 1, "https://example.com/tuna.jpg");
        cart.addItem(anotherItem);

        // Act
        cart.clear();

        // Assert
        assertTrue(cart.getItems().isEmpty());
        assertEquals(0, cart.getTotalPrice());
        assertEquals(0, cart.getTotalItems());
    }

    @Test
    void shouldCalculateTotalPriceCorrectly() {
        // Arrange
        cart.addItem(cartItem);
        CartItem anotherItem = new CartItem("product-2", "Fresh Tuna", 39.99, 1, "https://example.com/tuna.jpg");
        cart.addItem(anotherItem);

        // Act & Assert
        assertEquals(99.97, cart.getTotalPrice());
    }

    @Test
    void shouldToggleItemSelectionSuccessfully() {
        // Arrange
        cart.addItem(cartItem);

        // Act - Initially not selected
        cart.toggleItemSelection("product-1");

        // Assert - Should be selected
        assertTrue(cart.getSelectedItems().contains("product-1"));

        // Act - Toggle again
        cart.toggleItemSelection("product-1");

        // Assert - Should not be selected
        assertFalse(cart.getSelectedItems().contains("product-1"));
    }

    @Test
    void shouldHandleAddingSameProductTwice() {
        // Act
        cart.addItem(cartItem);
        cart.addItem(cartItem);

        // Assert - Should merge quantities
        assertEquals(1, cart.getItems().size());
        assertEquals(4, cart.getItems().get(0).getQuantity());
        assertEquals(119.96, cart.getTotalPrice());
    }

    @Test
    void shouldValidateItemQuantity() {
        // Act & Assert - Zero quantity
        assertThrows(IllegalArgumentException.class, () -> {
            new CartItem("product-1", "Fresh Salmon", 29.99, 0, "https://example.com/salmon.jpg");
        });

        // Act & Assert - Negative quantity
        assertThrows(IllegalArgumentException.class, () -> {
            new CartItem("product-1", "Fresh Salmon", 29.99, -1, "https://example.com/salmon.jpg");
        });
    }

    @Test
    void shouldValidateItemPrice() {
        // Act & Assert - Negative price
        assertThrows(IllegalArgumentException.class, () -> {
            new CartItem("product-1", "Fresh Salmon", -29.99, 2, "https://example.com/salmon.jpg");
        });
    }

    @Test
    void shouldSelectAllItems() {
        // Arrange
        cart.addItem(cartItem);
        CartItem anotherItem = new CartItem("product-2", "Fresh Tuna", 39.99, 1, "https://example.com/tuna.jpg");
        cart.addItem(anotherItem);

        // Act
        cart.selectAllItems();

        // Assert
        assertEquals(2, cart.getSelectedItems().size());
        assertTrue(cart.getSelectedItems().contains("product-1"));
        assertTrue(cart.getSelectedItems().contains("product-2"));
    }

    @Test
    void shouldDeselectAllItems() {
        // Arrange
        cart.addItem(cartItem);
        cart.selectAllItems();

        // Act
        cart.deselectAllItems();

        // Assert
        assertTrue(cart.getSelectedItems().isEmpty());
    }
}