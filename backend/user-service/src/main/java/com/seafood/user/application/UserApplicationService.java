package com.seafood.user.application;

import com.seafood.user.domain.model.User;
import com.seafood.user.domain.model.UserRepository;
import com.seafood.user.domain.model.UserRole;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

/**
 * User application service handling business logic for user management.
 * Provides methods for user registration, authentication, profile management,
 * and role-based access control for the seafood e-commerce platform.
 */
@Service
@RequiredArgsConstructor
public class UserApplicationService {
    private final UserRepository userRepository;

    public User loginOrRegister(String openId, String nickname, String avatarUrl) {
        return userRepository.findByOpenId(openId)
                .map(user -> {
                    user.updateProfile(nickname, avatarUrl);
                    return userRepository.save(user);
                })
                .orElseGet(() -> {
                    User newUser = new User(openId, nickname, avatarUrl, "", UserRole.USER);
                    return userRepository.save(newUser);
                });
    }

    public Optional<User> getUserById(String id) {
        return userRepository.findById(id);
    }

    public Optional<User> getUserByOpenId(String openId) {
        return userRepository.findByOpenId(openId);
    }

    public User getUserByPhone(String phone) {
        return userRepository.findByPhone(phone)
                .orElseThrow(() -> new IllegalArgumentException("User not found with phone: " + phone));
    }

    public java.util.List<User> getAllUsers() {
        return userRepository.findAll();
    }

    public User createUser(User user) {
        return userRepository.save(user);
    }

    public Optional<User> getUserByEmail(String email) {
        return userRepository.findByEmail(email);
    }

    public User updateUser(String id, String email, String phone, String address) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("User not found: " + id));
        if (email != null) user.setEmail(email);
        if (phone != null) user.setPhone(phone);
        if (address != null) user.setAddress(address);
        return userRepository.save(user);
    }

    public void deleteUser(String id) {
        userRepository.deleteById(id);
    }

    public User updateUserRole(String id, String role) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("User not found: " + id));

        if (role == null || role.trim().isEmpty()) {
            throw new IllegalArgumentException("Role cannot be null or empty");
        }

        UserRole userRole;
        try {
            userRole = UserRole.valueOf(role.toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Invalid role value: " + role + ". Valid values are: USER, ADMIN, MERCHANT");
        }

        user.setRole(userRole);
        return userRepository.save(user);
    }

    /**
     * Get all users with sorting
     *
     * @param sortBy sort field
     * @param sortDir sort direction
     * @return list of all users sorted
     */
    public List<User> getAllUsers(String sortBy, String sortDir) {
        return userRepository.findAll(sortBy, sortDir);
    }

    /**
     * Get users by role with sorting
     *
     * @param role the user role
     * @param sortBy sort field
     * @param sortDir sort direction
     * @return list of users with the specified role
     */
    public List<User> getUsersByRole(String role, String sortBy, String sortDir) {
        UserRole userRole;
        try {
            userRole = UserRole.valueOf(role.toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Invalid role value: " + role + ". Valid values are: USER, ADMIN, MERCHANT");
        }
        return userRepository.findByRole(userRole, sortBy, sortDir);
    }
}
