package com.seafood.product.infrastructure.persistence;

import com.seafood.product.domain.model.Product;
import com.seafood.product.domain.model.ProductRepository;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface MongoProductRepository extends ProductRepository, MongoRepository<Product, String> {
    List<Product> findByCategory(String category);

    // Find by keyword only (category is null or empty - search across all categories)
    @Query("{ $or: [ " +
            "{ 'name': { $regex: ?0, $options: 'i' } }, " +
            "{ 'description': { $regex: ?0, $options: 'i' } } " +
            "] }")
    org.springframework.data.domain.Page<Product> findByKeyword(
            String keyword, org.springframework.data.domain.Pageable pageable);

    // Find by keyword and category (both provided)
    @Query("{ $and: [ " +
           "{ $or: [ { 'name': { $regex: ?0, $options: 'i' } }, { 'description': { $regex: ?0, $options: 'i' } } ] }, " +
            "{ 'category': ?1 } " +
           "] }")
    org.springframework.data.domain.Page<Product> findByKeywordAndCategory(
            String keyword, String category, org.springframework.data.domain.Pageable pageable);
}
