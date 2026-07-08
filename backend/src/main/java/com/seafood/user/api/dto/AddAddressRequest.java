package com.seafood.user.api.dto;

import jakarta.validation.constraints.NotBlank;

public record AddAddressRequest(
        @NotBlank String name,
        @NotBlank String phone,
        @NotBlank String province,
        @NotBlank String city,
        String district,
        @NotBlank String detail,
        boolean isDefault
) {
}
