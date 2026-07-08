package com.seafood.testsupport.builders;

import com.seafood.user.domain.Address;
import com.seafood.shared.security.Role;
import com.seafood.user.domain.User;

import java.time.Instant;
import java.util.List;

/**
 * UserBuilder — D1 5 builder 之一。
 *
 * <p>核心字段:id / openId / nickname / role / phone / addresses。
 * avatarUrl / phone 默认 null 或空,需要时 withXxx()。
 */
public final class UserBuilder {

    private static final Instant DEFAULT_T = Instant.parse("2026-06-01T00:00:00Z");

    private String id = "u-test";
    private String openId = "dev-open-test";
    private String nickname = "测试用户";
    private String avatarUrl = null;
    private Role role = Role.CUSTOMER;
    private String phone = null;
    private List<Address> addresses = List.of();
    private List<String> favoriteProductIds = List.of();
    private Instant createdAt = DEFAULT_T;

    private UserBuilder() {}

    public static UserBuilder aUser() {
        return new UserBuilder();
    }

    public UserBuilder withId(String id) { this.id = id; return this; }
    public UserBuilder withOpenId(String openId) { this.openId = openId; return this; }
    public UserBuilder withNickname(String nickname) { this.nickname = nickname; return this; }
    public UserBuilder withAvatarUrl(String avatarUrl) { this.avatarUrl = avatarUrl; return this; }
    public UserBuilder withRole(Role role) { this.role = role; return this; }
    public UserBuilder withPhone(String phone) { this.phone = phone; return this; }
    public UserBuilder withAddresses(List<Address> addresses) { this.addresses = addresses; return this; }
    public UserBuilder withFavoriteProductIds(List<String> favoriteProductIds) { this.favoriteProductIds = favoriteProductIds; return this; }

    public User build() {
        return new User(id, openId, nickname, avatarUrl, role, phone, addresses, favoriteProductIds, createdAt);
    }
}