package com.seafood.order.application;

import com.seafood.order.domain.model.Order;
import com.seafood.order.domain.model.OrderItem;
import com.seafood.order.domain.model.OrderRepository;
import com.seafood.order.interfaces.rest.CreateOrderRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class OrderApplicationService {
    private final OrderRepository orderRepository;

    public Order createOrder(String userId, List<CreateOrderRequest.OrderItemRequest> itemRequests, BigDecimal totalAmount, String shippingAddress) {
        // 转换请求对象到领域对象
        List<OrderItem> items = itemRequests.stream()
                .map(req -> new OrderItem(
                        req.getProductId(),
                        req.getProductName(),
                        req.getPrice(),
                        req.getQuantity()
                ))
                .collect(Collectors.toList());

        Order order = new Order(userId, items, shippingAddress);
        
        // 验证总价是否匹配
        if (order.getTotalPrice().compareTo(totalAmount) != 0) {
            throw new RuntimeException("Total amount mismatch. Calculated: " + order.getTotalPrice() + ", Provided: " + totalAmount);
        }
        
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

    public Order updateOrderStatus(String orderId, String status) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new RuntimeException("Order not found"));
        
        OrderStatus newStatus = OrderStatus.valueOf(status);
        order.setStatus(newStatus);
        
        return orderRepository.save(order);
    }

    public void deleteOrder(String orderId) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new RuntimeException("Order not found"));
        orderRepository.delete(order);
    }
}
