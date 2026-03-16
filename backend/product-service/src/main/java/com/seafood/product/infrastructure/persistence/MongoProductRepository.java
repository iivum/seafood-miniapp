package com.seafood.product.infrastructure.persistence;

import com.seafood.product.domain.model.Product;
import com.seafood.product.domain.model.ProductRepository;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface MongoProductRepository extends ProductRepository, MongoRepository<Product, String> {
    List<Product> findByCategory(String category);
}
