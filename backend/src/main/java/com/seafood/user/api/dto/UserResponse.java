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
        Instant createdAt
) {
    public static UserResponse from(User u) {
        return new UserResponse(
                u.id(), u.openId(), u.nickname(), u.avatarUrl(),
                u.role().name(), u.phone(), u.addresses(), u.createdAt());
    }

    public static Role roleOf(UserResponse r) {
        return Role.valueOf(r.role());
    }
}
