package com.seafood.product.infrastructure.persistence;

import com.seafood.product.domain.model.Product;
import com.seafood.product.domain.model.ProductRepository;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface MongoProductRepository extends ProductRepository, MongoRepository<Product, String> {
    List<Product> findByCategory(String category);

    @Query("{ $and: [ " +
           "{ $or: [ { 'name': { $regex: ?0, $options: 'i' } }, { 'description': { $regex: ?0, $options: 'i' } } ] }, " +
           "{ $or: [ { 'category': ?1 }, { $expr: { $eq: [?1, null] } } ] } " +
           "] }")
    org.springframework.data.domain.Page<Product> findByKeywordAndCategory(
            String keyword, String category, org.springframework.data.domain.Pageable pageable);
}
