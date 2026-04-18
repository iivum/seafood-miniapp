package com.seafood.order.interfaces.rest.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AddressResponse {
    private String city;
    private String district;
    private String address;
    private String postalCode;
    private String receiverName;
    private String phone;
}
