package com.seafood.user.domain;

import com.seafood.shared.error.DomainException;
import com.seafood.shared.error.NotFoundException;
import com.seafood.shared.security.Role;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * User 聚合根 — 单一 record,role 字段充当 Customer/Admin 判别(参见 design.md §6.1)。
 *
 * <p>业务方法集中在聚合根;UserService 不直接操作字段,以便以后想分两类子类时只改这里。
 */
public record User(
        String id,
        String openId,
        String nickname,
        String avatarUrl,
        Role role,
        String phone,
        List<Address> addresses,
        Instant createdAt
) {

    public User {
        if (openId == null || openId.isBlank()) {
            throw new DomainException("openId 不能为空");
        }
        if (role == null) {
            throw new DomainException("role 不能为空");
        }
        addresses = addresses == null ? List.of() : List.copyOf(addresses);
    }

    // ----- 地址管理 -----

    /** 新增地址;若 isDefault=true 把其它地址的 default 取消(只能一个默认)。 */
    public User addAddress(Address newAddr) {
        if (newAddr == null) {
            throw new DomainException("地址不能为空");
        }
        String id = newAddr.id() == null || newAddr.id().isBlank()
                ? UUID.randomUUID().toString() : newAddr.id();
        Address normalized = new Address(id, newAddr.name(), newAddr.phone(),
                newAddr.province(), newAddr.city(), newAddr.district(), newAddr.detail(),
                newAddr.isDefault() || addresses.isEmpty());

        List<Address> next = new ArrayList<>(addresses.size() + 1);
        for (Address a : addresses) {
            next.add(new Address(a.id(), a.name(), a.phone(), a.province(),
                    a.city(), a.district(), a.detail(), normalized.isDefault() ? false : a.isDefault()));
        }
        next.add(normalized);
        return mutateAddresses(next);
    }

    public User updateAddress(String addressId, Address patch) {
        Address existing = findAddress(addressId);
        if (existing == null) {
            throw new NotFoundException("地址不存在:" + addressId);
        }
        Address merged = new Address(
                existing.id(),
                patch.name() == null || patch.name().isBlank() ? existing.name() : patch.name(),
                patch.phone() == null || patch.phone().isBlank() ? existing.phone() : patch.phone(),
                patch.province() == null ? existing.province() : patch.province(),
                patch.city() == null ? existing.city() : patch.city(),
                patch.district() == null ? existing.district() : patch.district(),
                patch.detail() == null ? existing.detail() : patch.detail(),
                patch.isDefault() || existing.isDefault()
        );
        List<Address> next = new ArrayList<>(addresses.size());
        for (Address a : addresses) {
            if (a.id().equals(addressId)) {
                next.add(merged);
            } else {
                next.add(new Address(a.id(), a.name(), a.phone(), a.province(),
                        a.city(), a.district(), a.detail(), merged.isDefault() ? false : a.isDefault()));
            }
        }
        return mutateAddresses(next);
    }

    public User removeAddress(String addressId) {
        if (findAddress(addressId) == null) {
            throw new NotFoundException("地址不存在:" + addressId);
        }
        List<Address> next = addresses.stream()
                .filter(a -> !a.id().equals(addressId))
                .toList();
        return mutateAddresses(next);
    }

    public User setDefaultAddress(String addressId) {
        Address target = findAddress(addressId);
        if (target == null) {
            throw new NotFoundException("地址不存在:" + addressId);
        }
        List<Address> next = new ArrayList<>(addresses.size());
        for (Address a : addresses) {
            next.add(new Address(a.id(), a.name(), a.phone(), a.province(),
                    a.city(), a.district(), a.detail(), a.id().equals(addressId)));
        }
        return mutateAddresses(next);
    }

    private Address findAddress(String id) {
        return addresses.stream().filter(a -> a.id().equals(id)).findFirst().orElse(null);
    }

    private User mutateAddresses(List<Address> next) {
        return new User(id, openId, nickname, avatarUrl, role, phone, next, createdAt);
    }

    // ----- role helpers -----

    public boolean isAdmin() { return role == Role.ADMIN; }
    public boolean isCustomer() { return role == Role.CUSTOMER; }
}
