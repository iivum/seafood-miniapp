package com.seafood.product.infra;

import com.seafood.product.domain.ProductStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.Optional;

public interface ProductRepository extends MongoRepository<ProductDocument, String> {

    Page<ProductDocument> findByStatus(ProductStatus status, Pageable pageable);

    Page<ProductDocument> findByCategory(String category, Pageable pageable);

    long countByStatus(ProductStatus status);

    long countByStock(int stock);

    Optional<ProductDocument> findFirstByName(String name);
}
