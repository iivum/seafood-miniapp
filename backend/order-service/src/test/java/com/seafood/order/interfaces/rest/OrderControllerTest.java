package com.seafood.order.interfaces.rest;

import com.seafood.order.application.OrderApplicationService;
import com.seafood.order.domain.model.Order;
import com.seafood.order.domain.model.OrderItem;
import com.seafood.order.domain.model.OrderStatus;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.Collections;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(OrderController.class)
class OrderControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private OrderApplicationService orderApplicationService;

    @Test
    void testCreateOrder() throws Exception {
        OrderItem item = new OrderItem("1", "Lobster", new BigDecimal("49.99"), 2);
        Order order = new Order("user1", Arrays.asList(item), new BigDecimal("99.98"), "123 Main St");
        order.setId("order1");

        when(orderApplicationService.createOrder(any(), any(), any(), any())).thenReturn(order);

        mockMvc.perform(post("/orders")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"userId\":\"user1\",\"items\":[{\"productId\":\"1\",\"productName\":\"Lobster\",\"price\":49.99,\"quantity\":2}],\"totalAmount\":99.98,\"shippingAddress\":\"123 Main St\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.userId").value("user1"))
                .andExpect(jsonPath("$.totalAmount").value(99.98));
    }

    @Test
    void testListAllOrders() throws Exception {
        OrderItem item = new OrderItem("1", "Lobster", new BigDecimal("49.99"), 2);
        Order order = new Order("user1", Arrays.asList(item), new BigDecimal("99.98"), "123 Main St");
        order.setId("order1");

        when(orderApplicationService.listAllOrders()).thenReturn(Arrays.asList(order));

        mockMvc.perform(get("/orders"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].userId").value("user1"))
                .andExpect(jsonPath("$[0].totalAmount").value(99.98));
    }

    @Test
    void testListAllOrdersEmpty() throws Exception {
        when(orderApplicationService.listAllOrders()).thenReturn(Collections.emptyList());

        mockMvc.perform(get("/orders"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isEmpty());
    }

    @Test
    void testGetOrderById() throws Exception {
        OrderItem item = new OrderItem("1", "Lobster", new BigDecimal("49.99"), 2);
        Order order = new Order("user1", Arrays.asList(item), new BigDecimal("99.98"), "123 Main St");
        order.setId("order1");

        when(orderApplicationService.getOrderById("order1")).thenReturn(Optional.of(order));

        mockMvc.perform(get("/orders/order1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value("order1"))
                .andExpect(jsonPath("$.userId").value("user1"));
    }

    @Test
    void testGetOrderByIdNotFound() throws Exception {
        when(orderApplicationService.getOrderById("nonexistent")).thenReturn(Optional.empty());

        mockMvc.perform(get("/orders/nonexistent"))
                .andExpect(status().isNotFound());
    }

    @Test
    void testGetOrdersByUserId() throws Exception {
        OrderItem item = new OrderItem("1", "Lobster", new BigDecimal("49.99"), 2);
        Order order = new Order("user1", Arrays.asList(item), new BigDecimal("99.98"), "123 Main St");
        order.setId("order1");

        when(orderApplicationService.getOrdersByUserId("user1")).thenReturn(Arrays.asList(order));

        mockMvc.perform(get("/orders/user/user1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].userId").value("user1"));
    }

    @Test
    void testUpdateOrderStatus() throws Exception {
        OrderItem item = new OrderItem("1", "Lobster", new BigDecimal("49.99"), 2);
        Order order = new Order("user1", Arrays.asList(item), new BigDecimal("99.98"), "123 Main St");
        order.setId("order1");
        order.setStatus(OrderStatus.SHIPPED);

        when(orderApplicationService.updateOrderStatus(any(), any())).thenReturn(order);

        mockMvc.perform(put("/orders/order1/status")
                .contentType(MediaType.TEXT_PLAIN)
                .content("SHIPPED"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SHIPPED"));
    }

    @Test
    void testDeleteOrder() throws Exception {
        mockMvc.perform(delete("/orders/order1"))
                .andExpect(status().isNoContent());
    }
}