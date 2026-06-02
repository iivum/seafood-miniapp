package com.seafood.user.domain;

import com.seafood.shared.security.Role;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;
import java.util.Set;

@Document(collection = "users")
public record User(
    @Id String id,
    @Indexed(unique = true) String username,
    String passwordHash,
    Role role,
    String displayName,
    Set<String> addresses,
    Instant createdAt,
    Instant updatedAt
) {
    public static User newCustomer(String username, String passwordHash, String displayName, Instant now) {
        return new User(null, username, passwordHash, Role.CUSTOMER, displayName, Set.of(), now, now);
    }

    public static User newAdmin(String username, String passwordHash, Instant now) {
        return new User(null, username, passwordHash, Role.ADMIN, "Administrator", Set.of(), now, now);
    }
}
