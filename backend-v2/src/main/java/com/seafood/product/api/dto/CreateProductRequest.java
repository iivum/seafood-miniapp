package com.seafood.product.api.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

public record CreateProductRequest(
    @NotBlank @Size(max = 128) String name,
    @Size(max = 1024) String description,
    @NotNull @DecimalMin("0.01") BigDecimal price,
    @PositiveOrZero int stock,
    @NotBlank @Size(max = 64) String category,
    @Size(max = 512) String imageUrl,
    boolean onSale
) {}
