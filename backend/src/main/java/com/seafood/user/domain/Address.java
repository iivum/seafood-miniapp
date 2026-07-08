package com.seafood.user.domain;

public record Address(
        String id,
        String name,
        String phone,
        String province,
        String city,
        String district,
        String detail,
        boolean isDefault
) {
}
