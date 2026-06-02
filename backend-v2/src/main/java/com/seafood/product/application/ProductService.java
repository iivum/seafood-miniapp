package com.seafood.product.application;

import com.seafood.product.api.dto.CreateProductRequest;
import com.seafood.product.api.dto.ProductListResponse;
import com.seafood.product.api.dto.ProductResponse;
import com.seafood.product.api.dto.UpdateProductRequest;
import com.seafood.product.domain.Product;
import com.seafood.product.infra.ProductMongoRepository;
import com.seafood.shared.error.DomainException;
import com.seafood.shared.error.ErrorCode;
import com.seafood.shared.error.NotFoundException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

import java.time.Instant;

@Service
public class ProductService {

    private final ProductMongoRepository products;

    public ProductService(ProductMongoRepository products) {
        this.products = products;
    }

    public ProductListResponse list(int page, int size, String category, String keyword) {
        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));
        Page<Product> result = page(category, keyword, pageable);
        return new ProductListResponse(
            result.getContent().stream().map(this::toResponse).toList(),
            result.getNumber(),
            result.getSize(),
            result.getTotalElements(),
            result.getTotalPages(),
            result.hasNext(),
            result.hasPrevious()
        );
    }

    public ProductResponse get(String id) {
        return toResponse(products.findById(id)
            .orElseThrow(() -> new NotFoundException("商品不存在")));
    }

    public ProductResponse create(CreateProductRequest req) {
        if (req.price().signum() <= 0) {
            throw new DomainException(ErrorCode.VALIDATION, "价格必须 > 0");
        }
        Instant now = Instant.now();
        Product p = Product.create(
            req.name(), req.description(), req.price(), req.stock(),
            req.category(), req.imageUrl(), req.onSale(), now
        );
        return toResponse(products.save(p));
    }

    public ProductResponse update(String id, UpdateProductRequest req) {
        Product existing = products.findById(id)
            .orElseThrow(() -> new NotFoundException("商品不存在"));
        if (req.price() != null && req.price().signum() <= 0) {
            throw new DomainException(ErrorCode.VALIDATION, "价格必须 > 0");
        }
        Instant now = Instant.now();
        Product updated = new Product(
            existing.id(),
            req.name() != null ? req.name() : existing.name(),
            req.description() != null ? req.description() : existing.description(),
            req.price() != null ? req.price() : existing.price(),
            req.stock() != null ? req.stock() : existing.stock(),
            req.category() != null ? req.category() : existing.category(),
            req.imageUrl() != null ? req.imageUrl() : existing.imageUrl(),
            req.onSale() != null ? req.onSale() : existing.onSale(),
            existing.createdAt(),
            now
        );
        return toResponse(products.save(updated));
    }

    public void delete(String id) {
        if (!products.existsById(id)) {
            throw new NotFoundException("商品不存在");
        }
        products.deleteById(id);
    }

    private Page<Product> page(String category, String keyword, Pageable pageable) {
        if (keyword != null && !keyword.isBlank()) {
            return products.findByNameContainingIgnoreCaseOrDescriptionContainingIgnoreCase(
                keyword, keyword, pageable);
        }
        if (category != null && !category.isBlank()) {
            return products.findByCategory(category, pageable);
        }
        return products.findAll(pageable);
    }

    private ProductResponse toResponse(Product p) {
        return new ProductResponse(
            p.id(), p.name(), p.description(), p.price(), p.stock(),
            p.category(), p.imageUrl(), p.onSale()
        );
    }
}
