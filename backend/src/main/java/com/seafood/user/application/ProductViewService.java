package com.seafood.user.application;

import com.seafood.product.api.dto.ProductResponse;
import com.seafood.product.application.ProductService;
import com.seafood.shared.error.NotFoundException;
import com.seafood.user.api.dto.ProductViewResponse;
import com.seafood.user.infra.ProductViewDocument;
import com.seafood.user.infra.ProductViewRepository;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/**
 * 浏览足迹服务(收藏 + 浏览足迹,design.md D1/D2)。
 *
 * <p>{@link #record}:同一商品反复查看只保留最新一条(按 userId+productId 查
 * 命中则刷新 viewedAt,不存在才插入),写入后裁剪超出 {@link #MAX_RECENT} 条的
 * 记录 —— 惰性裁剪(每次写入后查一次当前用户的全部足迹,超出部分整批删除),
 * 不用 TTL index(TTL 是按绝对时间过期,这里要的是"每人最近 N 条"的相对裁剪,
 * 语义不同,design.md D2)。
 */
@Service
public class ProductViewService {

    private static final int MAX_RECENT = 100;
    private static final String UNAVAILABLE_PRODUCT_NAME = "商品已下架";

    private final ProductViewRepository views;
    private final ProductService productService;

    public ProductViewService(ProductViewRepository views, ProductService productService) {
        this.views = views;
        this.productService = productService;
    }

    public void record(String userId, String productId) {
        ProductViewDocument doc = views.findByUserIdAndProductId(userId, productId).orElse(null);
        if (doc == null) {
            doc = new ProductViewDocument();
            doc.setUserId(userId);
            doc.setProductId(productId);
        }
        doc.setViewedAt(Instant.now());
        views.save(doc);
        prune(userId);
    }

    public List<ProductViewResponse> list(String userId) {
        return views.findByUserIdOrderByViewedAtDesc(userId).stream().map(this::enrich).toList();
    }

    public long countForUser(String userId) {
        return views.countByUserId(userId);
    }

    /** 按 viewedAt 降序取第 {@link #MAX_RECENT}+1 条开始的全部删除。 */
    private void prune(String userId) {
        List<ProductViewDocument> all = views.findByUserIdOrderByViewedAtDesc(userId);
        if (all.size() > MAX_RECENT) {
            views.deleteAll(all.subList(MAX_RECENT, all.size()));
        }
    }

    private ProductViewResponse enrich(ProductViewDocument d) {
        try {
            ProductResponse p = productService.get(d.getProductId());
            return new ProductViewResponse(d.getProductId(), p.name(), p.price(), p.imageUrl(), true, d.getViewedAt());
        } catch (NotFoundException e) {
            return new ProductViewResponse(d.getProductId(), UNAVAILABLE_PRODUCT_NAME, BigDecimal.ZERO, "", false, d.getViewedAt());
        }
    }
}
