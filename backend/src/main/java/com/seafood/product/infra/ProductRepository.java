package com.seafood.product.infra;

import com.seafood.product.domain.ProductStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;
import java.util.Optional;

public interface ProductRepository extends MongoRepository<ProductDocument, String> {

    Page<ProductDocument> findByStatus(ProductStatus status, Pageable pageable);

    Page<ProductDocument> findByCategory(String category, Pageable pageable);

    /**
     * fix-category-bad-status-500:公共分类浏览专用——查询级过滤 status，与
     * {@link #findByStatus} 的无分类分支语义对齐。区别于 {@link #findByCategory}
     * （无状态过滤，管理后台需要看到所有状态，含 admin 专用），这条方法只给
     * {@code ProductService.listPublic} 用，天然排除非 ACTIVE（含非法 status
     * 值）文档，不会触发 document→entity 转换阶段的枚举反序列化崩溃。
     */
    Page<ProductDocument> findByCategoryAndStatus(String category, ProductStatus status, Pageable pageable);

    long countByStatus(ProductStatus status);

    long countByStock(int stock);

    Optional<ProductDocument> findFirstByName(String name);

    /**
     * 路线图 2.18:库存低于阈值的所有商品(候选预警),调用方负责排序 + 截 top N。
     * 返回 List 而非 Page:预警列表通常只关心前 N 条,不需要分页元数据。
     * 50 条 seed 规模下顺序扫描无压力;万级后再加 stock 索引 + 改造为 Page。
     */
    List<ProductDocument> findByStockLessThan(int threshold);
}
