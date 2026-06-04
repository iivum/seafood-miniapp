package com.seafood.order.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;

public record CartItemRequest(
        @NotBlank String productId,
        @Positive int quantity
) {
}
