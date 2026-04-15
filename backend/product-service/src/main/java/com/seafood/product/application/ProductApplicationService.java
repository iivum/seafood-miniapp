package com.seafood.product.application;

import com.seafood.product.domain.model.Product;
import com.seafood.product.domain.model.ProductRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

/**
 * Product application service handling business logic for product operations.
 * Provides methods for CRUD operations on seafood products including
 * creation, retrieval, search, and inventory management.
 */
@Service
@RequiredArgsConstructor
public class ProductApplicationService {
    private final ProductRepository productRepository;

    public Product createProduct(String name, String description, BigDecimal price, int stock, String category, String imageUrl) {
        Product product = new Product(name, description, price, stock, category, imageUrl);
        return productRepository.save(product);
    }

    /**
     * 获取商品列表（分页 + 搜索）
     *
     * @param keyword  搜索关键词（商品名）
     * @param category 分类筛选
     * @param pageable 分页参数
     * @return 分页商品列表
     */
    public Page<Product> listProducts(String keyword, String category, Pageable pageable) {
        String effectiveKeyword = (keyword == null || keyword.isBlank()) ? "" : keyword;
        return productRepository.findByKeywordAndCategory(effectiveKeyword, category, pageable);
    }

    /**
     * 获取商品列表（分页 + 搜索）- 兼容旧方法签名
     */
    public Page<Product> listProducts(String keyword, String category, String dummy, int page, int pageSize) {
        String effectiveKeyword = (keyword == null || keyword.isBlank()) ? "" : keyword;
        Pageable pageable = PageRequest.of(page, pageSize, Sort.by(Sort.Direction.DESC, "id"));
        return productRepository.findByKeywordAndCategory(effectiveKeyword, category, pageable);
    }

    public List<Product> listAllProducts() {
        return productRepository.findAll();
    }

    public List<Product> findByCategory(String category) {
        return productRepository.findByCategory(category);
    }

    public Optional<Product> getProductById(String id) {
        return productRepository.findById(id);
    }

    public Product updateProductStock(String id, int quantity) {
        Product product = productRepository.findById(id).orElseThrow(() -> new RuntimeException("Product not found"));
        product.updateStock(quantity);
        return productRepository.save(product);
    }

    public void deleteProduct(String id) {
        productRepository.deleteById(id);
    }
}
