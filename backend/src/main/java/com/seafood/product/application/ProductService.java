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
import io.micrometer.core.instrument.MeterRegistry;
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
 *
 * <p>OpenSpec setup-observability-stack PR #3 — {@link #listPublic} 成功路径对每个返回的商品
 * 累加 {@code products.queried{category=...}} 计数器,tag value 是分类的中文 displayName
 * (与 {@link ProductResponse#category()} 一致)。category 维度基数 = 5(sealed interface),
 * 满足 design §D5 标签白名单约束(参见 ArchUnit
 * {@code MetricsCardinalityTest})。
 */
@Service
public class ProductService {

    private final ProductRepository repo;
    private final MeterRegistry meterRegistry;

    public ProductService(ProductRepository repo, MeterRegistry meterRegistry) {
        this.repo = repo;
        this.meterRegistry = meterRegistry;
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
        // 业务埋点:每个被浏览的商品 +1。tag value 是分类 displayName(5 档 sealed
        // interface,低基数),不用 category filter 维度。无结果时 0 增量,
        // 计数器只在真正被消费时增长,避免 0/全量导致 PromQL `rate()` 分母为零。
        for (ProductResponse r : mapped) {
            meterRegistry.counter("products.queried", "category", r.category()).increment();
        }
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
