package com.seafood.admin.service;

import com.seafood.admin.client.CreateProductRequest;
import com.seafood.admin.client.ProductClient;
import com.seafood.admin.client.ProductResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class ProductService {

    private static final Logger log = LoggerFactory.getLogger(ProductService.class);

    private final ProductClient productClient;

    public ProductService(ProductClient productClient) {
        this.productClient = productClient;
    }

    public List<ProductResponse> getAllProducts() {
        try {
            return productClient.getAllProducts();
        } catch (Exception e) {
            log.error("Failed to get all products", e);
            return List.of();
        }
    }

    public ProductResponse getProduct(String id) {
        return productClient.getProduct(id);
    }

    public ProductResponse createProduct(CreateProductRequest request) {
        validateProductRequest(request);
        return productClient.createProduct(request);
    }

    public ProductResponse updateProduct(String id, CreateProductRequest request) {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("Product ID is required");
        }
        validateProductRequest(request);
        return productClient.updateProduct(id, request);
    }

    public void deleteProduct(String id) {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("Product ID is required");
        }
        productClient.deleteProduct(id);
    }

    public int getLowStockCount() {
        return getAllProducts().stream()
            .filter(p -> p.getStock() < 10)
            .mapToInt(p -> 1)
            .sum();
    }

    private void validateProductRequest(CreateProductRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("Product request cannot be null");
        }
        if (request.getName() == null || request.getName().isBlank()) {
            throw new IllegalArgumentException("Product name is required");
        }
        if (request.getPrice() == null || request.getPrice().signum() <= 0) {
            throw new IllegalArgumentException("Product price must be positive");
        }
        if (request.getStock() < 0) {
            throw new IllegalArgumentException("Product stock cannot be negative");
        }
    }
}
