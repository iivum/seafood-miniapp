package com.seafood.user.api.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * 地址 upsert 请求 —— mp {@code address-edit} 的契约门面 DTO(POST/PUT 共用)。
 *
 * <p>mp 三级地区选择器产出 {@code province/city/district + detailAddress};而 domain
 * {@link com.seafood.user.domain.Address} 只有 {@code province/city/detail}(无 district),
 * 故在此把 {@code district + detailAddress} 折进 {@code detail}。折叠有损(回读 edit 页地区
 * 选择器无法回填 district),mp-07 列表展示不受影响。
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
    private String foldedDetail() {
        return district == null || district.isBlank() ? detailAddress : district + " " + detailAddress;
    }

    public AddAddressRequest toAddRequest() {
        return new AddAddressRequest(name, phone, province, city, foldedDetail(), isDefault);
    }

    public UpdateAddressRequest toUpdateRequest() {
        return new UpdateAddressRequest(name, phone, province, city, foldedDetail(), isDefault);
    }
}
