package com.seafood.admin.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@FeignClient(name = "order-service")
public interface OrderClient {
    @GetMapping("/orders")
    List<OrderResponse> getAllOrders();

    @GetMapping("/orders/{id}")
    OrderResponse getOrder(@PathVariable("id") String id);

    @PutMapping("/orders/{id}/status")
    OrderResponse updateOrderStatus(@PathVariable("id") String id, @RequestParam("status") String status);

    @PutMapping("/orders/{id}/ship")
    OrderResponse shipOrder(@PathVariable("id") String id, @RequestBody ShipOrderRequest request);
}
