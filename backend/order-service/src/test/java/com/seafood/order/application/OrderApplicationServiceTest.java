package com.seafood.order.application;

import com.seafood.order.domain.model.*;
import com.seafood.order.interfaces.rest.PaymentRequest;
import com.seafood.order.interfaces.rest.RefundRequest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * Test class for OrderApplicationService
 * Tests the application layer service operations for order management
 */
@ExtendWith(MockitoExtension.class)
class OrderApplicationServiceTest {

    @Mock
    private OrderRepository orderRepository;

    @Mock
    private CartApplicationService cartApplicationService;

    @Mock
    private PaymentService paymentService;

    @Mock
    private InventorySyncService inventorySyncService;

    @InjectMocks
    private OrderApplicationService orderApplicationService;

    private Order testOrder;
    private OrderItem testOrderItem;
    private Address testAddress;
    private Cart testCart;
    private CartItem testCartItem;

    @BeforeEach
    void setUp() {
        testAddress = new Address("John Doe", "13800138000", "123 Main St", "Beijing", "Chaoyang", "100000");
        testOrder = new Order("user-123", testAddress);
        testOrderItem = new OrderItem("product-1", "Fresh Salmon", 29.99, 2);
        testOrder.addItem(testOrderItem);
        testOrder.generateOrderNumber();

        testCart = new Cart("user-123");
        testCartItem = new CartItem("product-1", "Fresh Salmon", 29.99, 2, "https://example.com/salmon.jpg");
        testCart.addItem(testCartItem);
    }

    @Test
    void shouldCreateOrderFromCartSuccessfully() {
        // Arrange
        when(cartApplicationService.getCart(anyString())).thenReturn(testCart);
        when(cartApplicationService.clearCart(anyString())).thenReturn(testCart);
        when(orderRepository.save(any(Order.class))).thenAnswer(invocation -> invocation.getArgument(0));

        // Act
        Order result = orderApplicationService.createOrderFromCart("user-123", "cart-123");

        // Assert
        assertNotNull(result);
        assertEquals("user-123", result.getUserId());
        assertEquals(1, result.getItems().size());
        assertEquals(59.98, result.getTotalPrice());
        assertEquals(OrderStatus.PENDING_PAYMENT, result.getStatus());
        verify(orderRepository, times(1)).save(any(Order.class));
        verify(cartApplicationService, times(1)).clearCart("user-123");
    }

    @Test
    void shouldProcessPaymentSuccessfully() {
        // Arrange
        PaymentRequest paymentRequest = new PaymentRequest("wechat", "100.00", "user-123", "order-123");
        when(orderRepository.findById(anyString())).thenReturn(Optional.of(testOrder));
        when(paymentService.processPayment(any(PaymentRequest.class))).thenReturn("transaction-123");
        when(orderRepository.save(any(Order.class))).thenAnswer(invocation -> invocation.getArgument(0));

        // Act
        Order result = orderApplicationService.processPayment("order-123", paymentRequest);

        // Assert
        assertNotNull(result);
        assertEquals(OrderStatus.PAID, result.getStatus());
        assertEquals("transaction-123", result.getTransactionId());
        verify(orderRepository, times(1)).save(any(Order.class));
        verify(paymentService, times(1)).processPayment(any(PaymentRequest.class));
        verify(inventorySyncService, times(1)).reduceInventory(anyList());
    }

    @Test
    void shouldShipOrderSuccessfully() {
        // Arrange
        testOrder.markAsPaid("transaction-123");
        when(orderRepository.findById(anyString())).thenReturn(Optional.of(testOrder));
        when(orderRepository.save(any(Order.class))).thenAnswer(invocation -> invocation.getArgument(0));

        // Act
        Order result = orderApplicationService.shipOrder("order-123", "tracking-123");

        // Assert
        assertNotNull(result);
        assertEquals(OrderStatus.SHIPPED, result.getStatus());
        assertEquals("tracking-123", result.getTrackingNumber());
        verify(orderRepository, times(1)).save(any(Order.class));
    }

    @Test
    void shouldCompleteOrderSuccessfully() {
        // Arrange
        testOrder.markAsPaid("transaction-123");
        testOrder.markAsShipped("tracking-123");
        when(orderRepository.findById(anyString())).thenReturn(Optional.of(testOrder));
        when(orderRepository.save(any(Order.class))).thenAnswer(invocation -> invocation.getArgument(0));

        // Act
        Order result = orderApplicationService.completeOrder("order-123");

        // Assert
        assertNotNull(result);
        assertEquals(OrderStatus.DELIVERED, result.getStatus());
        verify(orderRepository, times(1)).save(any(Order.class));
    }

    @Test
    void shouldCancelOrderSuccessfully() {
        // Arrange
        when(orderRepository.findById(anyString())).thenReturn(Optional.of(testOrder));
        when(orderRepository.save(any(Order.class))).thenAnswer(invocation -> invocation.getArgument(0));

        // Act
        Order result = orderApplicationService.cancelOrder("order-123", "User requested cancellation");

        // Assert
        assertNotNull(result);
        assertEquals(OrderStatus.CANCELLED, result.getStatus());
        assertEquals("User requested cancellation", result.getCancellationReason());
        verify(orderRepository, times(1)).save(any(Order.class));
    }

    @Test
    void shouldProcessRefundSuccessfully() {
        // Arrange
        testOrder.markAsPaid("transaction-123");
        testOrder.markAsShipped("tracking-123");
        testOrder.markAsDelivered();
        RefundRequest refundRequest = new RefundRequest("50.00", "Product quality issue", "user-123", "order-123");
        when(orderRepository.findById(anyString())).thenReturn(Optional.of(testOrder));
        when(paymentService.processRefund(any(RefundRequest.class))).thenReturn("refund-123");
        when(orderRepository.save(any(Order.class))).thenAnswer(invocation -> invocation.getArgument(0));

        // Act
        Order result = orderApplicationService.processRefund("order-123", refundRequest);

        // Assert
        assertNotNull(result);
        assertEquals(OrderStatus.REFUNDED, result.getStatus());
        assertEquals("refund-123", result.getRefundTransactionId());
        verify(orderRepository, times(1)).save(any(Order.class));
        verify(paymentService, times(1)).processRefund(any(RefundRequest.class));
    }

    @Test
    void shouldGetOrdersByUserIdAndStatus() {
        // Arrange
        when(orderRepository.findByUserIdAndStatus(anyString(), any(OrderStatus.class)))
                .thenReturn(Arrays.asList(testOrder));

        // Act
        List<Order> result = orderApplicationService.getOrdersByUserIdAndStatus("user-123", OrderStatus.PENDING_PAYMENT);

        // Assert
        assertNotNull(result);
        assertEquals(1, result.size());
        assertEquals(testOrder.getId(), result.get(0).getId());
        verify(orderRepository, times(1)).findByUserIdAndStatus("user-123", OrderStatus.PENDING_PAYMENT);
    }

    @Test
    void shouldGetOrderStatistics() {
        // Arrange
        when(orderRepository.findOrdersCreatedBetween(any(), any()))
                .thenReturn(Arrays.asList(testOrder));

        // Act
        OrderApplicationService.OrderStatistics result = orderApplicationService.getOrderStatistics(
                "user-123",
                LocalDate.now().minusDays(7),
                LocalDate.now()
        );

        // Assert
        assertNotNull(result);
        assertEquals(1, result.getTotalOrders());
        verify(orderRepository, times(1)).findOrdersCreatedBetween(any(), any());
    }

    @Test
    void shouldHandleOrderNotFound() {
        // Arrange
        when(orderRepository.findById(anyString())).thenReturn(Optional.empty());

        // Act & Assert
        assertThrows(OrderNotFoundException.class, () -> {
            orderApplicationService.processPayment("invalid-order", new PaymentRequest("wechat", "100.00", "user-123", "invalid-order"));
        });
    }

    @Test
    void shouldHandleInvalidOrderStatusTransition() {
        // Arrange
        when(orderRepository.findById(anyString())).thenReturn(Optional.of(testOrder));
        // Order is PENDING_PAYMENT, cannot ship

        // Act & Assert
        assertThrows(IllegalStateException.class, () -> {
            orderApplicationService.shipOrder("order-123", "tracking-123");
        });
    }
}