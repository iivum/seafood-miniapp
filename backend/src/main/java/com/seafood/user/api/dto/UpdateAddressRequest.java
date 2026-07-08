package com.seafood.user.api.dto;


/** 部分字段可选;null 字段不覆盖原值。{@code isDefault=true} 强制置为默认。 */
public record UpdateAddressRequest(
        String name,
        String phone,
        String province,
        String city,
        String district,
        String detail,
        boolean isDefault
) {
}
