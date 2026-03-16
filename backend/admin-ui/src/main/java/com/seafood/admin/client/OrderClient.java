package com.seafood.admin.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;

import java.util.List;

@FeignClient(name = "order-service")
public interface OrderClient {
    @GetMapping("/orders")
    List<OrderResponse> getAllOrders();
}
