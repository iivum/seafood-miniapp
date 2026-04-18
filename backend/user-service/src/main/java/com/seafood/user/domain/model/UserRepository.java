package com.seafood.user.domain.model;

import java.util.List;
import java.util.Optional;

public interface UserRepository {
    User save(User user);
    Optional<User> findById(String id);
    Optional<User> findByOpenId(String openId);
    Optional<User> findByPhone(String phone);
    Optional<User> findByEmail(String email);
    List<User> findAll();
    List<User> findAll(String sortBy, String sortDir);
    void deleteById(String id);
    List<User> findByRole(UserRole role);
    List<User> findByRole(UserRole role, String sortBy, String sortDir);
}
