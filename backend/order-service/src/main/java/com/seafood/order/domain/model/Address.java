package com.seafood.order.domain.model;

import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
public class Address {

    private String name; // 收件人姓名
    private String phone; // 手机号
    private String detailAddress; // 详细地址
    private String city; // 市
    private String province; // 省
    private String district; // 区/县

    public Address(String name, String phone, String detailAddress, String city, String province, String district) {
        this.name = name;
        this.phone = phone;
        this.detailAddress = detailAddress;
        this.city = city;
        this.province = province;
        this.district = district;
    }

    public String getFullAddress() {
        return (province == null ? "" : province) +
               (city == null ? "" : city) +
               (district == null ? "" : district) +
               (detailAddress == null ? "" : detailAddress);
    }
}