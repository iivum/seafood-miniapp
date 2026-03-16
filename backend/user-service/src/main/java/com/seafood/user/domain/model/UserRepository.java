package com.seafood.user.domain.model;

import java.util.Optional;

public interface UserRepository {
    User save(User user);
    Optional<User> findById(String id);
    Optional<User> findByOpenId(String openId);
    java.util.List<User> findAll();
}
