package com.seafood.admin.client;

import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
public class ProductResponse {
    private String id;
    private String name;
    private String description;
    private BigDecimal price;
    private int stock;
    private String category;
    private String imageUrl;
    private boolean onSale;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
