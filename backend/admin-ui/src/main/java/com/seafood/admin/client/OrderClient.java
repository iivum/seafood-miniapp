package com.seafood.admin.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@FeignClient(name = "order-service")
public interface OrderClient {
    @GetMapping("/api/orders/all")
    List<OrderResponse> getAllOrders();

    @GetMapping("/api/orders/{id}")
    OrderResponse getOrder(@PathVariable("id") String id);

    @PutMapping("/api/orders/{id}/status")
    OrderResponse updateOrderStatus(@PathVariable("id") String id, @RequestParam("status") String status);

    @PutMapping("/api/orders/{id}/ship")
    OrderResponse shipOrder(@PathVariable("id") String id, @RequestBody ShipOrderRequest request);
}
