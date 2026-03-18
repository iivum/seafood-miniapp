package com.seafood.order.application;

import com.seafood.order.domain.model.Address;
import com.seafood.order.domain.model.AddressRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class AddressApplicationService {
    
    private final AddressRepository addressRepository;

    public Address createAddress(String userId, String name, String phone, 
                                 String province, String city, String district, 
                                 String detailAddress, boolean isDefault) {
        
        // 验证地址信息
        validateAddress(name, phone, province, city, district, detailAddress);
        
        // 如果设置为默认地址，先取消该用户的其他默认地址
        if (isDefault) {
            unsetDefaultAddress(userId);
        }
        
        Address address = new Address(userId, name, phone, province, city, district, detailAddress, isDefault);
        return addressRepository.save(address);
    }

    public Address updateAddress(String id, String name, String phone, 
                                 String province, String city, String district, 
                                 String detailAddress, boolean isDefault) {
        
        // 验证地址信息
        validateAddress(name, phone, province, city, district, detailAddress);
        
        Address address = addressRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Address not found: " + id));
        
        // 如果设置为默认地址，先取消该用户的其他默认地址
        if (isDefault && !address.isDefault()) {
            unsetDefaultAddress(address.getUserId());
        }
        
        address.update(name, phone, province, city, district, detailAddress, isDefault);
        return addressRepository.save(address);
    }

    public void deleteAddress(String id) {
        Address address = addressRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Address not found: " + id));
        
        // 如果删除的是默认地址，需要设置一个新的默认地址（如果存在）
        if (address.isDefault()) {
            List<Address> userAddresses = addressRepository.findByUserId(address.getUserId());
            userAddresses.stream()
                .filter(addr -> !addr.getId().equals(id))
                .findFirst()
                .ifPresent(addr -> {
                    addr.setDefault(true);
                    addressRepository.save(addr);
                });
        }
        
        addressRepository.delete(address);
    }

    public List<Address> getUserAddresses(String userId) {
        return addressRepository.findByUserId(userId);
    }

    public Optional<Address> getAddressById(String id) {
        return addressRepository.findById(id);
    }

    public Address setDefaultAddress(String id) {
        Address address = addressRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Address not found: " + id));
        
        // 取消当前默认地址
        unsetDefaultAddress(address.getUserId());
        
        // 设置新的默认地址
        address.setDefault(true);
        return addressRepository.save(address);
    }

    public void unsetDefaultAddress(String userId) {
        addressRepository.findByUserId(userId).stream()
                .filter(Address::isDefault)
                .forEach(addr -> {
                    addr.setDefault(false);
                    addressRepository.save(addr);
                });
    }

    public Optional<Address> getDefaultAddress(String userId) {
        return addressRepository.findByUserId(userId).stream()
                .filter(Address::isDefault)
                .findFirst();
    }

    private void validateAddress(String name, String phone, String province, 
                                 String city, String district, String detailAddress) {
        
        // 验证姓名
        if (name == null || name.trim().length() < 2) {
            throw new RuntimeException("收件人姓名至少需要2个字符");
        }
        
        // 验证手机号
        if (phone == null || !phone.matches("^1[3-9]\\d{9}$")) {
            throw new RuntimeException("请输入正确的手机号");
        }
        
        // 验证地址信息
        if (province == null || province.isEmpty() || 
            city == null || city.isEmpty() || 
            district == null || district.isEmpty()) {
            throw new RuntimeException("请选择完整的省市区");
        }
        
        // 验证详细地址
        if (detailAddress == null || detailAddress.trim().length() < 5) {
            throw new RuntimeException("详细地址至少需要5个字符");
        }
    }
}