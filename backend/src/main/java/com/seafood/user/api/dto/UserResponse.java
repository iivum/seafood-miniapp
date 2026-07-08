package com.seafood.user.api.dto;

import com.seafood.shared.security.Role;
import com.seafood.user.domain.Address;
import com.seafood.user.domain.User;

import java.time.Instant;
import java.util.List;

public record UserResponse(
        String id,
        String openId,
        String nickname,
        String avatarUrl,
        String role,
        String phone,
        List<Address> addresses,
        Instant createdAt,
        int favoriteCount,
        int viewCount
) {
    public static UserResponse from(User u, long viewCount) {
        return new UserResponse(
                u.id(), u.openId(), u.nickname(), u.avatarUrl(),
                u.role().name(), u.phone(), u.addresses(), u.createdAt(),
                u.favoriteProductIds().size(), (int) viewCount);
    }

    public static Role roleOf(UserResponse r) {
        return Role.valueOf(r.role());
    }
}
