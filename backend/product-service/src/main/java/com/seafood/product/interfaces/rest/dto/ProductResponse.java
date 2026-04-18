package com.seafood.product.interfaces.rest.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
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
