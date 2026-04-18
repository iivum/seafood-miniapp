package com.seafood.order.domain.model;

import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Address entity representing a shipping address for order delivery.
 *
 * <p>This entity encapsulates recipient information including contact details
 * and location hierarchy (province, city, district) needed for logistics
 * fulfillment and package routing.</p>
 *
 * <p>An address can be marked as default, indicating the user's preferred
 * shipping location for faster checkout. The system ensures only one
 * default address per user at any given time.</p>
 *
 * @see Order
 */
@Data
@NoArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "_class")
public class Address {

    private String id;
    private String userId;
    /** Recipient name for delivery contact */
    private String name;
    /** Contact phone number for delivery notifications */
    private String phone;
    /** Detailed street address for final routing */
    private String detailAddress;
    /** City name for logistics routing */
    private String city;
    /** Province/State name for regional classification */
    private String province;
    /** District/County name for precise location */
    private String district;
    /** Whether this is the user's default shipping address */
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