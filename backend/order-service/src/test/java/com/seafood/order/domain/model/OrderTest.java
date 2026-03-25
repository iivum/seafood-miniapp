package com.seafood.order.domain.model;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import static org.junit.jupiter.api.Assertions.*;

import java.util.List;

/**
 * Test class for Order aggregate root
 * Follows TDD approach to define order behavior before implementation
 */
class OrderTest {

    private Order order;
    private OrderItem orderItem;
    private Address address;

    @BeforeEach
    void setUp() {
        address = new Address("John Doe", "13800138000", "123 Main St", "Beijing", "Chaoyang", "100000");
        order = new Order("user-123", address);
        orderItem = new OrderItem("product-1", "Fresh Salmon", 29.99, 2);
    }

    @Test
    void shouldCreateOrderSuccessfully() {
        // Assert
        assertNotNull(order);
        assertEquals("user-123", order.getUserId());
        assertEquals(address, order.getShippingAddress());
        assertEquals(OrderStatus.PENDING_PAYMENT, order.getStatus());
        assertTrue(order.getItems().isEmpty());
        assertEquals(0, order.getTotalPrice());
        assertNull(order.getOrderNumber());
        assertNull(order.getCreatedAt());
    }

    @Test
    void shouldAddItemToOrderSuccessfully() {
        // Act
        order.addItem(orderItem);

        // Assert
        assertEquals(1, order.getItems().size());
        assertEquals(orderItem, order.getItems().get(0));
        assertEquals(59.98, order.getTotalPrice());
    }

    @Test
    void shouldAddMultipleItemsToOrder() {
        // Arrange
        OrderItem anotherItem = new OrderItem("product-2", "Fresh Tuna", 39.99, 1);

        // Act
        order.addItem(orderItem);
        order.addItem(anotherItem);

        // Assert
        assertEquals(2, order.getItems().size());
        assertEquals(99.97, order.getTotalPrice());
    }

    @Test
    void shouldGenerateOrderNumberOnCreation() {
        // Act
        order.generateOrderNumber();

        // Assert
        assertNotNull(order.getOrderNumber());
        assertTrue(order.getOrderNumber().startsWith("SF"));
        assertEquals(16, order.getOrderNumber().length());
    }

    @Test
    void shouldTransitionToPaidStatus() {
        // Arrange
        order.generateOrderNumber();
        order.addItem(orderItem);

        // Act
        order.markAsPaid("transaction-123");

        // Assert
        assertEquals(OrderStatus.PAID, order.getStatus());
        assertEquals("transaction-123", order.getTransactionId());
        assertNotNull(order.getPaidAt());
    }

    @Test
    void shouldTransitionToShippedStatus() {
        // Arrange
        order.generateOrderNumber();
        order.addItem(orderItem);
        order.markAsPaid("transaction-123");

        // Act
        order.markAsShipped("tracking-123");

        // Assert
        assertEquals(OrderStatus.SHIPPED, order.getStatus());
        assertEquals("tracking-123", order.getTrackingNumber());
        assertNotNull(order.getShippedAt());
    }

    @Test
    void shouldTransitionToDeliveredStatus() {
        // Arrange
        order.generateOrderNumber();
        order.addItem(orderItem);
        order.markAsPaid("transaction-123");
        order.markAsShipped("tracking-123");

        // Act
        order.markAsDelivered();

        // Assert
        assertEquals(OrderStatus.DELIVERED, order.getStatus());
        assertNotNull(order.getDeliveredAt());
    }

    @Test
    void shouldCancelOrderSuccessfully() {
        // Arrange
        order.generateOrderNumber();
        order.addItem(orderItem);

        // Act
        order.cancel("User requested cancellation");

        // Assert
        assertEquals(OrderStatus.CANCELLED, order.getStatus());
        assertEquals("User requested cancellation", order.getCancellationReason());
        assertNotNull(order.getCancelledAt());
    }

    @Test
    void shouldNotAllowInvalidStatusTransitions() {
        // Act & Assert - Cannot ship unpaid order
        assertThrows(IllegalStateException.class, () -> {
            order.generateOrderNumber();
            order.addItem(orderItem);
            order.markAsShipped("tracking-123");
        });

        // Act & Assert - Cannot deliver unshipped order
        assertThrows(IllegalStateException.class, () -> {
            order.generateOrderNumber();
            order.addItem(orderItem);
            order.markAsPaid("transaction-123");
            order.markAsDelivered();
        });

        // Act & Assert - Cannot pay cancelled order
        assertThrows(IllegalStateException.class, () -> {
            order.generateOrderNumber();
            order.addItem(orderItem);
            order.cancel("User request");
            order.markAsPaid("transaction-123");
        });
    }

    @Test
    void shouldCalculateTotalPriceCorrectly() {
        // Arrange
        order.addItem(orderItem);
        OrderItem anotherItem = new OrderItem("product-2", "Fresh Tuna", 39.99, 1);
        order.addItem(anotherItem);

        // Act & Assert
        assertEquals(99.97, order.getTotalPrice());
    }

    @Test
    void shouldUpdateOrderNote() {
        // Arrange
        String note = "Please deliver before 6 PM";

        // Act
        order.updateNote(note);

        // Assert
        assertEquals(note, order.getNote());
    }

    @Test
    void shouldUpdateShippingAddress() {
        // Arrange
        Address newAddress = new Address("Jane Doe", "13900139000", "456 Oak St", "Shanghai", "Pudong", "200000");

        // Act
        order.updateShippingAddress(newAddress);

        // Assert
        assertEquals(newAddress, order.getShippingAddress());
    }

    @Test
    void shouldValidateOrderBeforeSubmission() {
        // Arrange
        order.generateOrderNumber();
        order.addItem(orderItem);

        // Act
        boolean isValid = order.validate();

        // Assert
        assertTrue(isValid);
    }

    @Test
    void shouldNotValidateEmptyOrder() {
        // Arrange
        order.generateOrderNumber();

        // Act
        boolean isValid = order.validate();

        // Assert
        assertFalse(isValid);
    }

    @Test
    void shouldNotValidateOrderWithoutOrderNumber() {
        // Arrange
        order.addItem(orderItem);

        // Act
        boolean isValid = order.validate();

        // Assert
        assertFalse(isValid);
    }
}