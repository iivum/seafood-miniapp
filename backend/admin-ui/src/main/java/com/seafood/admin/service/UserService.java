package com.seafood.admin.service;

import com.seafood.admin.client.UserClient;
import com.seafood.admin.client.UserResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class UserService {

    private static final Logger log = LoggerFactory.getLogger(UserService.class);

    private final UserClient userClient;

    public UserService(UserClient userClient) {
        this.userClient = userClient;
    }

    public List<UserResponse> getAllUsers() {
        try {
            return userClient.getAllUsers();
        } catch (Exception e) {
            log.error("Failed to get all users", e);
            return List.of();
        }
    }

    public UserResponse getUser(String id) {
        return userClient.getUser(id);
    }

    public UserResponse getUserByOpenId(String openId) {
        if (openId == null || openId.isBlank()) {
            throw new IllegalArgumentException("OpenID is required");
        }
        return userClient.getUserByOpenId(openId);
    }

    public UserResponse updateUserRole(String id, String role) {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("User ID is required");
        }
        if (role == null || role.isBlank()) {
            throw new IllegalArgumentException("Role is required");
        }
        return userClient.updateUserRole(id, role);
    }

    public long countAdminUsers() {
        return getAllUsers().stream()
            .filter(u -> "ADMIN".equals(u.getRole()))
            .count();
    }
}
