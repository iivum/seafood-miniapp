package com.seafood.order.interfaces.rest;

import com.seafood.order.application.CartApplicationService;
import com.seafood.order.domain.model.Cart;
import com.seafood.order.domain.model.CartItem;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * Test class for CartController
 * Tests REST API endpoints for cart operations
 */
@ExtendWith(MockitoExtension.class)
class CartControllerTest {

    @Mock
    private CartApplicationService cartApplicationService;

    @InjectMocks
    private CartController cartController;

    private Cart testCart;
    private CartItem testItem;
    private AddToCartRequest addToCartRequest;
    private UpdateCartItemRequest updateCartItemRequest;

    @BeforeEach
    void setUp() {
        testCart = new Cart("user-123");
        testItem = new CartItem("product-1", "Fresh Salmon", 29.99, 2, "https://example.com/salmon.jpg");
        testCart.addItem(testItem);

        addToCartRequest = new AddToCartRequest("product-2", "Fresh Tuna", 39.99, 1, "https://example.com/tuna.jpg");
        updateCartItemRequest = new UpdateCartItemRequest("product-1", 5);
    }

    @Test
    void shouldGetCartSuccessfully() {
        // Arrange
        when(cartApplicationService.getOrCreateCart(anyString())).thenReturn(testCart);

        // Act
        ResponseEntity<CartResponse> response = cartController.getCart("user-123");

        // Assert
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals(testCart.getId(), response.getBody().getId());
        verify(cartApplicationService, times(1)).getOrCreateCart("user-123");
    }

    @Test
    void shouldAddItemToCartSuccessfully() {
        // Arrange
        when(cartApplicationService.addItemToCart(anyString(), anyString(), anyInt(), anyString(), anyDouble(), anyString()))
                .thenReturn(testCart);

        // Act
        ResponseEntity<CartResponse> response = cartController.addToCart("user-123", addToCartRequest);

        // Assert
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        verify(cartApplicationService, times(1)).addItemToCart(
                "user-123",
                addToCartRequest.getProductId(),
                addToCartRequest.getQuantity(),
                addToCartRequest.getName(),
                addToCartRequest.getPrice(),
                addToCartRequest.getImageUrl()
        );
    }

    @Test
    void shouldUpdateCartItemSuccessfully() {
        // Arrange
        when(cartApplicationService.updateItemQuantity(anyString(), anyString(), anyInt()))
                .thenReturn(testCart);

        // Act
        ResponseEntity<CartResponse> response = cartController.updateCartItem("user-123", updateCartItemRequest);

        // Assert
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        verify(cartApplicationService, times(1)).updateItemQuantity(
                "user-123",
                updateCartItemRequest.getItemId(),
                updateCartItemRequest.getQuantity()
        );
    }

    @Test
    void shouldRemoveItemFromCartSuccessfully() {
        // Arrange
        when(cartApplicationService.removeItemFromCart(anyString(), anyString()))
                .thenReturn(testCart);

        // Act
        ResponseEntity<CartResponse> response = cartController.removeItemFromCart("user-123", "product-1");

        // Assert
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        verify(cartApplicationService, times(1)).removeItemFromCart("user-123", "product-1");
    }

    @Test
    void shouldClearCartSuccessfully() {
        // Arrange
        when(cartApplicationService.clearCart(anyString())).thenReturn(testCart);

        // Act
        ResponseEntity<CartResponse> response = cartController.clearCart("user-123");

        // Assert
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        verify(cartApplicationService, times(1)).clearCart("user-123");
    }

    @Test
    void shouldToggleItemSelectionSuccessfully() {
        // Arrange
        when(cartApplicationService.toggleItemSelection(anyString(), anyString()))
                .thenReturn(testCart);

        // Act
        ResponseEntity<CartResponse> response = cartController.toggleItemSelection("user-123", "product-1");

        // Assert
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        verify(cartApplicationService, times(1)).toggleItemSelection("user-123", "product-1");
    }

    @Test
    void shouldHandleValidationErrors() {
        // Arrange - Invalid request
        AddToCartRequest invalidRequest = new AddToCartRequest("", "", null, 0, "");

        // Act
        ResponseEntity<CartResponse> response = cartController.addToCart("user-123", invalidRequest);

        // Assert - Should return bad request
        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
    }

    @Test
    void shouldHandleServiceErrors() {
        // Arrange
        when(cartApplicationService.getOrCreateCart(anyString()))
                .thenThrow(new RuntimeException("Service error"));

        // Act
        ResponseEntity<CartResponse> response = cartController.getCart("user-123");

        // Assert - Should return internal server error
        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getStatusCode());
    }

    @Test
    void shouldConvertCartToResponseSuccessfully() {
        // Arrange
        List<CartItem> items = Arrays.asList(testItem);
        Cart cart = new Cart("user-123");
        cart.getItems().addAll(items);
        cart.updateTotals();

        // Act
        CartResponse response = CartController.convertToResponse(cart);

        // Assert
        assertNotNull(response);
        assertEquals(cart.getId(), response.getId());
        assertEquals(cart.getUserId(), response.getUserId());
        assertEquals(cart.getItems().size(), response.getItems().size());
        assertEquals(cart.getTotalPrice(), response.getTotalPrice());
        assertEquals(cart.getTotalItems(), response.getTotalItems());
    }

    @Test
    void shouldConvertCartItemToResponseSuccessfully() {
        // Act
        CartItemResponse response = CartController.convertToResponse(testItem, true);

        // Assert
        assertNotNull(response);
        assertEquals(testItem.getId(), response.getId());
        assertEquals(testItem.getProductId(), response.getProductId());
        assertEquals(testItem.getName(), response.getName());
        assertEquals(testItem.getPrice(), response.getPrice());
        assertEquals(testItem.getQuantity(), response.getQuantity());
        assertEquals(testItem.getImageUrl(), response.getImageUrl());
        assertTrue(response.isSelected());
    }

    @Test
    void shouldHandleEmptyCart() {
        // Arrange
        Cart emptyCart = new Cart("user-123");
        when(cartApplicationService.getOrCreateCart(anyString())).thenReturn(emptyCart);

        // Act
        ResponseEntity<CartResponse> response = cartController.getCart("user-123");

        // Assert
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals(0, response.getBody().getItems().size());
        assertEquals(0, response.getBody().getTotalPrice());
        assertEquals(0, response.getBody().getTotalItems());
    }

    @Test
    void shouldHandleCartWithMultipleItems() {
        // Arrange
        CartItem anotherItem = new CartItem("product-2", "Fresh Tuna", 39.99, 1, "https://example.com/tuna.jpg");
        testCart.addItem(anotherItem);
        when(cartApplicationService.getOrCreateCart(anyString())).thenReturn(testCart);

        // Act
        ResponseEntity<CartResponse> response = cartController.getCart("user-123");

        // Assert
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(2, response.getBody().getItems().size());
        assertEquals(99.97, response.getBody().getTotalPrice());
        assertEquals(3, response.getBody().getTotalItems());
    }
}