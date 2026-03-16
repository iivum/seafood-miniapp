package com.seafood.user.infrastructure.persistence;

import com.seafood.user.domain.model.User;
import com.seafood.user.domain.model.UserRepository;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface MongoUserRepository extends UserRepository, MongoRepository<User, String> {
    Optional<User> findByOpenId(String openId);
}
