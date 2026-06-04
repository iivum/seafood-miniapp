package com.seafood.user.infra;

import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.Optional;

/** 唯一入口:AuthService 与 UserService 都用此接口。 */
public interface UserRepository extends MongoRepository<UserDocument, String> {
    Optional<UserDocument> findByOpenId(String openId);
}
