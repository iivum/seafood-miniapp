package com.seafood.admin.client;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.List;

@Component
public class ServiceFallbackFactory {

    @Value("${feign.fallback.enabled:true}")
    private boolean fallbackEnabled;

    public ProductClient createProductFallback() {
        return new ProductClient() {
            @Override
            public List<ProductResponse> getAllProducts() {
                return Collections.emptyList();
            }

            @Override
            public ProductResponse getProduct(String id) {
                return null;
            }

            @Override
            public ProductResponse createProduct(CreateProductRequest request) {
                return null;
            }

            @Override
            public ProductResponse updateProduct(String id, CreateProductRequest request) {
                return null;
            }

            @Override
            public void deleteProduct(String id) {
            }
        };
    }

    public OrderClient createOrderFallback() {
        return new OrderClient() {
            @Override
            public List<OrderResponse> getAllOrders() {
                return Collections.emptyList();
            }

            @Override
            public OrderResponse getOrder(String id) {
                return null;
            }

            @Override
            public OrderResponse updateOrderStatus(String id, String status) {
                return null;
            }

            @Override
            public OrderResponse shipOrder(String id, ShipOrderRequest request) {
                return null;
            }
        };
    }

    public UserClient createUserFallback() {
        return new UserClient() {
            @Override
            public List<UserResponse> getAllUsers() {
                return Collections.emptyList();
            }

            @Override
            public UserResponse getUser(String id) {
                return null;
            }

            @Override
            public UserResponse getUserByOpenId(String openId) {
                return null;
            }

            @Override
            public UserResponse updateUserRole(String id, String role) {
                return null;
            }
        };
    }
}
