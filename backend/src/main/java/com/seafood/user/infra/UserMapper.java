package com.seafood.user.infra;

import com.seafood.shared.security.Role;
import com.seafood.user.domain.User;

import java.time.Instant;

public final class UserMapper {

    private UserMapper() {}

    public static User toDomain(UserDocument d) {
        if (d == null) return null;
        Role role = d.getRole() == null ? Role.CUSTOMER : Role.valueOf(d.getRole());
        return new User(
                d.getId(),
                d.getOpenId(),
                d.getNickname(),
                d.getAvatarUrl(),
                role,
                d.getPhone(),
                d.getAddresses(),
                d.getFavoriteProductIds(),
                d.getCreatedAt());
    }

    public static UserDocument toDocument(User u) {
        UserDocument d = new UserDocument();
        d.setId(u.id());
        d.setOpenId(u.openId());
        d.setNickname(u.nickname());
        d.setAvatarUrl(u.avatarUrl());
        d.setRole(u.role().name());
        d.setPhone(u.phone());
        d.setAddresses(u.addresses() == null ? java.util.List.of() : u.addresses());
        d.setFavoriteProductIds(u.favoriteProductIds() == null ? java.util.List.of() : u.favoriteProductIds());
        d.setCreatedAt(u.createdAt() == null ? Instant.now() : u.createdAt());
        return d;
    }
}
