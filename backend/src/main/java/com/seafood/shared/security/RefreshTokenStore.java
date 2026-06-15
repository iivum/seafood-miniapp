package com.seafood.shared.security;

import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;
import java.util.Optional;

/**
 * 已签发 refresh token 的"消费"记录(用于 2.5 单次使用 + family 撤销)。
 * 一个 family 对应一次登录;family 内任一 jti 被重用 → 整族撤销。
 */
public interface RefreshTokenStore extends MongoRepository<RefreshTokenRecord, String> {
    Optional<RefreshTokenRecord> findByJti(String jti);
    List<RefreshTokenRecord> findByFamilyId(String familyId);
}

