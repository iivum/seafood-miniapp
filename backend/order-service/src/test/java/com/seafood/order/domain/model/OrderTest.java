package com.seafood.order.domain.model;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

class OrderTest {

    @Test
    void testCreateOrder() {
        List<OrderItem> items = new ArrayList<>();
        items.add(new OrderItem("p1", "Lobster", new BigDecimal("49.99"), 2));
        
        Order order = new Order("user1", items, "123 Main St");
        
        assertEquals("user1", order.getUserId());
        assertEquals(new BigDecimal("99.98"), order.getTotalPrice());
        assertEquals(OrderStatus.PENDING_PAYMENT, order.getStatus());
    }

    @Test
    void testPayOrder() {
        List<OrderItem> items = new ArrayList<>();
        items.add(new OrderItem("p1", "Lobster", new BigDecimal("49.99"), 1));
        Order order = new Order("user1", items, "123 Main St");
        
        order.pay();
        assertEquals(OrderStatus.PAID, order.getStatus());
    }

    @Test
    void testCancelOrder() {
        List<OrderItem> items = new ArrayList<>();
        items.add(new OrderItem("p1", "Lobster", new BigDecimal("49.99"), 1));
        Order order = new Order("user1", items, "123 Main St");
        
        order.cancel();
        assertEquals(OrderStatus.CANCELLED, order.getStatus());
    }
}
