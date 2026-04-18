package com.seafood.order.interfaces.rest;

import com.seafood.order.application.OrderApplicationService;
import com.seafood.order.domain.model.Address;
import com.seafood.order.domain.model.Order;
import com.seafood.order.domain.model.OrderNotFoundException;
import com.seafood.order.domain.model.OrderStatus;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(OrderController.class)
@AutoConfigureMockMvc(addFilters = false)
class OrderControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private OrderApplicationService orderApplicationService;

    private Order testOrder;
    private CreateOrderRequest createOrderRequest;
    private PaymentRequest paymentRequest;
    private RefundRequest refundRequest;

    @BeforeEach
    void setUp() {
        // Create test address
        Address testAddress = new Address("张三", "13800138000", "北京市朝阳区某街道", "北京", "北京市", "朝阳区");

        // Create test order
        testOrder = new Order("user123", testAddress);
        testOrder.setId("order123");
        testOrder.setOrderNumber("ORD-20260325-001");
        testOrder.setStatus(OrderStatus.PENDING_PAYMENT);

        // Create test requests
        createOrderRequest = new CreateOrderRequest("user123", "cart123", "address123", Arrays.asList("item1", "item2"));
        paymentRequest = new PaymentRequest("wechat", "100.00", "user123", "order123");
        refundRequest = new RefundRequest("50.00", "Product quality issue", "user123", "order123");
    }

    @Test
    void shouldCreateOrderFromCartSuccessfully() throws Exception {
        when(orderApplicationService.createOrderFromCart(anyString(), anyString()))
                .thenReturn(testOrder);

        mockMvc.perform(post("/api/orders")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(createOrderRequest)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value("order123"))
                .andExpect(jsonPath("$.orderNumber").value("ORD-20260325-001"))
                .andExpect(jsonPath("$.status").value("PENDING_PAYMENT"));
    }

    @Test
    void shouldCreateOrderFromCartWhenCartIsEmpty() throws Exception {
        when(orderApplicationService.createOrderFromCart(anyString(), anyString()))
                .thenThrow(new IllegalArgumentException("Cart is empty"));

        mockMvc.perform(post("/api/orders")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(createOrderRequest)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Cart is empty"));
    }

    @Test
    void shouldGetOrderByIdSuccessfully() throws Exception {
        when(orderApplicationService.getOrderById(anyString())).thenReturn(testOrder);

        mockMvc.perform(get("/api/orders/{id}", "order123"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value("order123"))
                .andExpect(jsonPath("$.orderNumber").value("ORD-20260325-001"))
                .andExpect(jsonPath("$.status").value("PENDING_PAYMENT"));
    }

    @Test
    void shouldGetOrderByIdWhenOrderNotFound() throws Exception {
        when(orderApplicationService.getOrderById(anyString()))
                .thenThrow(new OrderNotFoundException("Order not found: order999"));

        mockMvc.perform(get("/api/orders/{id}", "order999"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Order not found: order999"));
    }

    @Test
    void shouldGetOrdersByUserAndStatusSuccessfully() throws Exception {
        List<Order> orders = Arrays.asList(testOrder);
        when(orderApplicationService.getOrdersByUserIdAndStatus(any(), any(), any(), any()))
                .thenReturn(orders);

        mockMvc.perform(get("/api/orders")
                .param("userId", "user123")
                .param("status", "PENDING_PAYMENT"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value("order123"))
                .andExpect(jsonPath("$[0].status").value("PENDING_PAYMENT"));
    }

    @Test
    void shouldProcessPaymentSuccessfully() throws Exception {
        Address testAddress = new Address("张三", "13800138000", "北京市朝阳区某街道", "北京", "北京市", "朝阳区");
        Order paidOrder = new Order("user123", testAddress);
        paidOrder.setId("order123");
        paidOrder.setOrderNumber("ORD-20260325-001");
        paidOrder.setStatus(OrderStatus.PAID);
        paidOrder.setTransactionId("TXN-12345");

        when(orderApplicationService.processPayment(anyString(), any()))
                .thenReturn(paidOrder);

        mockMvc.perform(post("/api/orders/{id}/payment", "order123")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(paymentRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PAID"))
                .andExpect(jsonPath("$.transactionId").value("TXN-12345"));
    }

    @Test
    void shouldProcessPaymentWhenOrderStatusInvalid() throws Exception {
        when(orderApplicationService.processPayment(anyString(), any()))
                .thenThrow(new IllegalStateException("Order cannot be paid in current status: CANCELLED"));

        mockMvc.perform(post("/api/orders/{id}/payment", "order123")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(paymentRequest)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Order cannot be paid in current status: CANCELLED"));
    }

    @Test
    void shouldShipOrderSuccessfully() throws Exception {
        Address testAddress = new Address("张三", "13800138000", "北京市朝阳区某街道", "北京", "北京市", "朝阳区");
        Order shippedOrder = new Order("user123", testAddress);
        shippedOrder.setId("order123");
        shippedOrder.setOrderNumber("ORD-20260325-001");
        shippedOrder.setStatus(OrderStatus.SHIPPED);
        shippedOrder.setTrackingNumber("TRACK-12345");

        when(orderApplicationService.shipOrder(anyString(), anyString()))
                .thenReturn(shippedOrder);

        mockMvc.perform(put("/api/orders/{id}/ship", "order123")
                .param("trackingNumber", "TRACK-12345"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SHIPPED"))
                .andExpect(jsonPath("$.trackingNumber").value("TRACK-12345"));
    }

    @Test
    void shouldCompleteOrderSuccessfully() throws Exception {
        Address testAddress = new Address("张三", "13800138000", "北京市朝阳区某街道", "北京", "北京市", "朝阳区");
        Order completedOrder = new Order("user123", testAddress);
        completedOrder.setId("order123");
        completedOrder.setOrderNumber("ORD-20260325-001");
        completedOrder.setStatus(OrderStatus.DELIVERED);

        when(orderApplicationService.completeOrder(anyString()))
                .thenReturn(completedOrder);

        mockMvc.perform(put("/api/orders/{id}/complete", "order123"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("DELIVERED"));
    }

    @Test
    void shouldCancelOrderSuccessfully() throws Exception {
        Address testAddress = new Address("张三", "13800138000", "北京市朝阳区某街道", "北京", "北京市", "朝阳区");
        Order cancelledOrder = new Order("user123", testAddress);
        cancelledOrder.setId("order123");
        cancelledOrder.setOrderNumber("ORD-20260325-001");
        cancelledOrder.setStatus(OrderStatus.CANCELLED);

        when(orderApplicationService.cancelOrder(anyString(), anyString()))
                .thenReturn(cancelledOrder);

        mockMvc.perform(put("/api/orders/{id}/cancel", "order123")
                .param("reason", "User request"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"));
    }

    @Test
    void shouldProcessRefundSuccessfully() throws Exception {
        Address testAddress = new Address("张三", "13800138000", "北京市朝阳区某街道", "北京", "北京市", "朝阳区");
        Order refundedOrder = new Order("user123", testAddress);
        refundedOrder.setId("order123");
        refundedOrder.setOrderNumber("ORD-20260325-001");
        refundedOrder.setStatus(OrderStatus.REFUNDED);

        when(orderApplicationService.processRefund(anyString(), any()))
                .thenReturn(refundedOrder);

        mockMvc.perform(post("/api/orders/{id}/refund", "order123")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(refundRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("REFUNDED"));
    }

    @Test
    void shouldGetOrderStatisticsSuccessfully() throws Exception {
        OrderApplicationService.OrderStatistics stats = new OrderApplicationService.OrderStatistics(
                10, 1000.0, 100.0, 2, 3, 2, 2, 1, 0
        );

        when(orderApplicationService.getOrderStatistics(anyString(), any(), any()))
                .thenReturn(stats);

        mockMvc.perform(get("/api/orders/{id}/statistics", "user123")
                .param("startDate", "2026-01-01")
                .param("endDate", "2026-12-31"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalOrders").value(10))
                .andExpect(jsonPath("$.totalAmount").value(1000.0))
                .andExpect(jsonPath("$.averageOrderValue").value(100.0));
    }

    @Test
    void shouldHandleValidationErrors() throws Exception {
        CreateOrderRequest invalidRequest = new CreateOrderRequest("", "", "", null);

        mockMvc.perform(post("/api/orders")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(invalidRequest)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").exists());
    }
}