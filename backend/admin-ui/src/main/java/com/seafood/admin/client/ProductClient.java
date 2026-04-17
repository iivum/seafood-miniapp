package com.seafood.admin.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@FeignClient(name = "product-service")
public interface ProductClient {
    @GetMapping("/products/all")
    List<ProductResponse> getAllProducts();

    @GetMapping("/products/{id}")
    ProductResponse getProduct(@PathVariable("id") String id);

    @PostMapping("/products")
    ProductResponse createProduct(@RequestBody CreateProductRequest request);

    @PutMapping("/products/{id}")
    ProductResponse updateProduct(@PathVariable("id") String id, @RequestBody CreateProductRequest request);

    @DeleteMapping("/products/{id}")
    void deleteProduct(@PathVariable("id") String id);
}
