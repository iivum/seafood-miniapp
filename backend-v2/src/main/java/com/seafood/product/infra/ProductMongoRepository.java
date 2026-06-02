package com.seafood.product.infra;

import com.seafood.product.domain.Product;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface ProductMongoRepository extends MongoRepository<Product, String> {
    Page<Product> findByCategory(String category, Pageable pageable);
    Page<Product> findByOnSaleTrue(Pageable pageable);
    Page<Product> findByNameContainingIgnoreCaseOrDescriptionContainingIgnoreCase(
        String name, String description, Pageable pageable);
}
