package com.seafood.product.domain.model;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.Optional;

public interface ProductRepository {
    Product save(Product product);
    Optional<Product> findById(String id);
    List<Product> findAll();
    List<Product> findByCategory(String category);
    Page<Product> findByKeywordAndCategory(String keyword, String category, Pageable pageable);
    void deleteById(String id);
}
