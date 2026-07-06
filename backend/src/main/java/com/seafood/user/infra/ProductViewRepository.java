package com.seafood.user.infra;

import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;
import java.util.Optional;

public interface ProductViewRepository extends MongoRepository<ProductViewDocument, String> {
    Optional<ProductViewDocument> findByUserIdAndProductId(String userId, String productId);
    List<ProductViewDocument> findByUserIdOrderByViewedAtDesc(String userId);
    long countByUserId(String userId);
}
