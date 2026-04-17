package com.seafood.order.domain.model;

import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "_class")
public class Address {

    private String id;
    private String userId;
    private String name; // 收件人姓名
    private String phone; // 手机号
    private String detailAddress; // 详细地址
    private String city; // 市
    private String province; // 省
    private String district; // 区/县
    private boolean isDefault;

    public Address(String name, String phone, String detailAddress, String city, String province, String district) {
        this.name = name;
        this.phone = phone;
        this.detailAddress = detailAddress;
        this.city = city;
        this.province = province;
        this.district = district;
    }

    public Address(String userId, String name, String phone, String province, String city, String district, String detailAddress, boolean isDefault) {
        this.userId = userId;
        this.name = name;
        this.phone = phone;
        this.province = province;
        this.city = city;
        this.district = district;
        this.detailAddress = detailAddress;
        this.isDefault = isDefault;
    }

    public String getFullAddress() {
        return (province == null ? "" : province) +
               (city == null ? "" : city) +
               (district == null ? "" : district) +
               (detailAddress == null ? "" : detailAddress);
    }

    public void update(String name, String phone, String province, String city, String district, String detailAddress, boolean isDefault) {
        this.name = name;
        this.phone = phone;
        this.province = province;
        this.city = city;
        this.district = district;
        this.detailAddress = detailAddress;
        this.isDefault = isDefault;
    }

    public void setDefault(boolean isDefault) {
        this.isDefault = isDefault;
    }

    public boolean isDefault() {
        return isDefault;
    }
}