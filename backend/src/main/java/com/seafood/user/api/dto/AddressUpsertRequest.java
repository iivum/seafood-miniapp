package com.seafood.user.api.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * 地址 upsert 请求 —— mp {@code address-edit} 的契约门面 DTO(POST/PUT 共用)。
 *
 * <p>mp 三级地区选择器产出 {@code province/city/district + detailAddress};domain
 * {@link com.seafood.user.domain.Address} 同样有独立的 {@code district} 字段(design.md D4),
 * 故这里原样透传,不做任何折叠 —— {@code detailAddress}(mp 字段名)映射到 domain 的
 * {@code detail}(字段名不同,但同样不折叠)。
 *
 * <p>额外的 {@code id}/{@code userId} 字段由 mp 携带但后端忽略(身份取自 JWT principal)。
 */
public record AddressUpsertRequest(
        @NotBlank String name,
        @NotBlank String phone,
        @NotBlank String province,
        @NotBlank String city,
        String district,
        @NotBlank String detailAddress,
        boolean isDefault
) {
    public AddAddressRequest toAddRequest() {
        return new AddAddressRequest(name, phone, province, city, district, detailAddress, isDefault);
    }

    public UpdateAddressRequest toUpdateRequest() {
        return new UpdateAddressRequest(name, phone, province, city, district, detailAddress, isDefault);
    }
}
