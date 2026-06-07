package com.seafood.product.application;

import com.seafood.product.api.dto.ProductRequest;
import com.seafood.product.api.dto.ProductResponse;
import com.seafood.product.domain.Product;
import com.seafood.product.domain.ProductCategory;
import com.seafood.product.domain.ProductStatus;
import com.seafood.product.infra.ProductDocument;
import com.seafood.product.infra.ProductMapper;
import com.seafood.product.infra.ProductRepository;
import com.seafood.shared.error.NotFoundException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;

/**
 * 商品写服务 + 公共读(参见 specs/backend-api §Public product browsing / §Admin product management)。
 *
 * <p>约束:
 * <ul>
 *   <li>所有写操作要求 {@code hasRole('ADMIN')},由 Controller @PreAuthorize 守门</li>
 *   <li>公共读不强制登录,但只返回 status == ACTIVE 的商品</li>
 *   <li>{@link #decrementStock} 是给订单服务用的入口,跨模块唯一接口</li>
 * </ul>
 */
@Service
public class ProductService {

    private final ProductRepository repo;

    public ProductService(ProductRepository repo) {
        this.repo = repo;
    }

    // ----- 写(ADMIN)-----

    public ProductResponse create(ProductRequest req) {
        Instant now = Instant.now();
        Product p = new Product(
                null, req.name(), req.description(), req.price(), req.stock(),
                ProductCategory.of(req.category()), req.imageUrl(),
                ProductStatus.ACTIVE, now, now);
        ProductDocument saved = repo.save(ProductMapper.toDocument(p));
        return ProductResponse.from(ProductMapper.toDomain(saved));
    }

    public ProductResponse update(String id, ProductRequest req) {
        ProductDocument d = repo.findById(id)
                .orElseThrow(() -> new NotFoundException("商品不存在:" + id));
        Product current = ProductMapper.toDomain(d);
        Product updated = current.updateBasics(req.name(), req.description(), req.price(), req.imageUrl());
        // stock 走专用方法
        if (req.stock() != current.stock()) {
            int delta = req.stock() - current.stock();
            if (delta < 0) {
                updated = updated.decrementStock(-delta);
            } else {
                updated = new Product(updated.id(), updated.name(), updated.description(),
                        updated.price(), req.stock(), updated.category(),
                        updated.imageUrl(), updated.status(),
                        updated.createdAt(), Instant.now());
            }
        }
        ProductDocument saved = repo.save(ProductMapper.toDocument(updated));
        return ProductResponse.from(ProductMapper.toDomain(saved));
    }

    public void delete(String id) {
        if (!repo.existsById(id)) {
            throw new NotFoundException("商品不存在:" + id);
        }
        repo.deleteById(id);
    }

    public ProductResponse discontinue(String id) {
        ProductDocument d = repo.findById(id)
                .orElseThrow(() -> new NotFoundException("商品不存在:" + id));
        Product current = ProductMapper.toDomain(d);
        Product updated = current.withStatus(ProductStatus.DISCONTINUED);
        ProductDocument saved = repo.save(ProductMapper.toDocument(updated));
        return ProductResponse.from(ProductMapper.toDomain(saved));
    }

    // ----- 公共读 -----

    public ProductResponse get(String id) {
        ProductDocument d = repo.findById(id)
                .orElseThrow(() -> new NotFoundException("商品不存在:" + id));
        return ProductResponse.from(ProductMapper.toDomain(d));
    }

    public Page<ProductResponse> listPublic(String category, Pageable pageable) {
        Page<ProductDocument> page = (category == null || category.isBlank())
                ? repo.findByStatus(ProductStatus.ACTIVE, pageable)
                : repo.findByCategory(category, pageable)
                        .map(d -> { d.setStatus(ProductStatus.ACTIVE); return d; });
        List<ProductResponse> mapped = page.getContent().stream()
                .map(ProductMapper::toDomain)
                .map(ProductResponse::from)
                .toList();
        return new PageImpl<>(mapped, pageable, page.getTotalElements());
    }

    public Page<ProductResponse> listAdmin(String category, Pageable pageable) {
        Page<ProductDocument> page = (category == null || category.isBlank())
                ? repo.findAll(pageable)
                : repo.findByCategory(category, pageable);
        List<ProductResponse> mapped = page.getContent().stream()
                .map(ProductMapper::toDomain)
                .map(ProductResponse::from)
                .toList();
        return new PageImpl<>(mapped, pageable, page.getTotalElements());
    }

    // ----- 跨模块入口(给 OrderService 调)-----

    public Product decrementStock(String productId, int quantity) {
        ProductDocument d = repo.findById(productId)
                .orElseThrow(() -> new NotFoundException("商品不存在:" + productId));
        Product updated = ProductMapper.toDomain(d).decrementStock(quantity);
        ProductDocument saved = repo.save(ProductMapper.toDocument(updated));
        return ProductMapper.toDomain(saved);
    }
}
