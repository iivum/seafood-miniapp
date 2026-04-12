package com.seafood.user.domain.model;

import java.util.Optional;

public interface UserRepository {
    User save(User user);
    Optional<User> findById(String id);
    Optional<User> findByOpenId(String openId);
    Optional<User> findByPhone(String phone);
    Optional<User> findByEmail(String email);
    java.util.List<User> findAll();
    void deleteById(String id);
}
