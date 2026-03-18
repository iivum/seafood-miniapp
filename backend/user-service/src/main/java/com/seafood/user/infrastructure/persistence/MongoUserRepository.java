package com.seafood.user.infrastructure.persistence;

import com.seafood.user.domain.model.User;
import com.seafood.user.domain.model.UserRepository;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface MongoUserRepository extends UserRepository, MongoRepository<User, String> {
    Optional<User> findByOpenId(String openId);
    
    @Override
    default User findByPhone(String phone) {
        // 由于MongoRepository没有直接支持findByPhone的方法
        // 我们需要通过findAll来查找（实际生产环境应该添加索引和查询方法）
        return findAll().stream()
                .filter(user -> phone.equals(user.getPhone()))
                .findFirst()
                .orElse(null);
    }
}
