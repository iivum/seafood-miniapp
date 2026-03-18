package com.seafood.order.domain.model;

import com.seafood.common.domain.AggregateRoot;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.mongodb.core.mapping.Document;

@Getter
@Setter
@NoArgsConstructor
@Document(collection = "addresses")
public class Address extends AggregateRoot<String> {
    
    private String userId;
    private String name; // 收件人姓名
    private String phone; // 手机号
    private String province; // 省
    private String city; // 市
    private String district; // 区/县
    private String detailAddress; // 详细地址
    private boolean isDefault; // 是否为默认地址

    public Address(String userId, String name, String phone, String province, 
                  String city, String district, String detailAddress, boolean isDefault) {
        super();
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

    public void update(String name, String phone, String province, 
                      String city, String district, String detailAddress, boolean isDefault) {
        this.name = name;
        this.phone = phone;
        this.province = province;
        this.city = city;
        this.district = district;
        this.detailAddress = detailAddress;
        this.isDefault = isDefault;
    }
}