package com.seafood.admin.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@FeignClient(name = "product-service")
public interface ProductClient {
    @GetMapping("/products")
    List<ProductResponse> getAllProducts();

    @PostMapping("/products")
    ProductResponse createProduct(@RequestBody CreateProductRequest request);

    @DeleteMapping("/products/{id}")
    void deleteProduct(@PathVariable("id") String id);
}

@lombok.Data
class CreateProductRequest {
    private String name;
    private String description;
    private java.math.BigDecimal price;
    private int stock;
    private String category;
    private String imageUrl;
}
