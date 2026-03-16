package com.seafood.order.application;

import com.seafood.order.domain.model.Order;
import com.seafood.order.domain.model.OrderItem;
import com.seafood.order.domain.model.OrderRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class OrderApplicationService {
    private final OrderRepository orderRepository;

    public Order createOrder(String userId, List<OrderItem> items, String shippingAddress) {
        Order order = new Order(userId, items, shippingAddress);
        return orderRepository.save(order);
    }

    public List<Order> getOrdersByUser(String userId) {
        return orderRepository.findByUserId(userId);
    }

    public Optional<Order> getOrderById(String id) {
        return orderRepository.findById(id);
    }

    public Order payOrder(String orderId) {
        Order order = orderRepository.findById(orderId).orElseThrow(() -> new RuntimeException("Order not found"));
        order.pay();
        return orderRepository.save(order);
    }

    public Order cancelOrder(String orderId) {
        Order order = orderRepository.findById(orderId).orElseThrow(() -> new RuntimeException("Order not found"));
        order.cancel();
        return orderRepository.save(order);
    }

    public List<Order> listAllOrders() {
        return orderRepository.findAll();
    }
}
