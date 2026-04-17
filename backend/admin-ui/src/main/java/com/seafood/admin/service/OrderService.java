package com.seafood.admin.service;

import com.seafood.admin.client.OrderClient;
import com.seafood.admin.client.OrderResponse;
import com.seafood.admin.client.ShipOrderRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.List;

@Service
public class OrderService {

    private static final Logger log = LoggerFactory.getLogger(OrderService.class);

    private final OrderClient orderClient;

    public OrderService(OrderClient orderClient) {
        this.orderClient = orderClient;
    }

    public List<OrderResponse> getAllOrders() {
        try {
            return orderClient.getAllOrders();
        } catch (Exception e) {
            log.error("Failed to get all orders", e);
            return List.of();
        }
    }

    public OrderResponse getOrder(String id) {
        return orderClient.getOrder(id);
    }

    public OrderResponse updateOrderStatus(String id, String status) {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("Order ID is required");
        }
        if (status == null || status.isBlank()) {
            throw new IllegalArgumentException("Status is required");
        }
        return orderClient.updateOrderStatus(id, status);
    }

    public OrderResponse shipOrder(String id, ShipOrderRequest request) {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("Order ID is required");
        }
        if (request == null) {
            throw new IllegalArgumentException("Ship order request cannot be null");
        }
        return orderClient.shipOrder(id, request);
    }

    public long countByStatus(String status) {
        return getAllOrders().stream()
            .filter(o -> status.equals(o.getStatus()))
            .count();
    }

    public BigDecimal calculateRevenue() {
        return getAllOrders().stream()
            .filter(o -> "PAID".equals(o.getStatus()) ||
                        "SHIPPED".equals(o.getStatus()) ||
                        "COMPLETED".equals(o.getStatus()))
            .map(OrderResponse::getTotalPrice)
            .reduce(BigDecimal.ZERO, BigDecimal::add);
    }
}
