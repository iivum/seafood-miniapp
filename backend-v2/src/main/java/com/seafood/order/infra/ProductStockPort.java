package com.seafood.order.infra;

import com.seafood.product.domain.Product;
import com.seafood.product.infra.ProductMongoRepository;
import com.seafood.shared.error.DomainException;
import com.seafood.shared.error.ErrorCode;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Anti-corruption layer: order module accesses product stock through this port
 * rather than directly through ProductMongoRepository. Keeps bounded contexts clean.
 */
@Component
public class ProductStockPort {

    private final ProductMongoRepository products;

    public ProductStockPort(ProductMongoRepository products) {
        this.products = products;
    }

    public Product get(String productId) {
        return products.findById(productId)
            .orElseThrow(() -> new DomainException(ErrorCode.NOT_FOUND, "商品不存在: " + productId));
    }

    public List<Product> getAll(List<String> ids) {
        return products.findAllById(ids);
    }
}
