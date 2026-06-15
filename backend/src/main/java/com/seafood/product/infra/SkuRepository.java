package com.seafood.product.infra;

import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;

/**
 * SKU 数据访问层(路线图 3.8 — ad-04 SKU 行内编辑基础)。
 *
 * <p>设计要点:
 * <ul>
 *   <li>SKU 存储形式有 2 选 1:(a) 嵌入 Product 文档(本实现),(b) 独立 collection。
 *       嵌入选型:SKU 数 ≤ 50 / 单商品,1:N 关系清晰;读 SKU 跟随读 Product,
 *       无 N+1 问题。</li>
 *   <li>若 SKU 增删改频繁,后续可切独立 collection(参见 tasks.md §5.13 注)。</li>
 * </ul>
 *
 * <p>本接口暂未直接使用(SKU 走 ProductDocument.skus 嵌入),保留接口为 Sprint 4 切
 * 独立 collection 备用。{@code MongoRepository<ProductDocument, String>} 是同一 collection
 * (products),所以查询 SKU 等价于 {@code findById} 后取 {@code skus} 字段。
 *
 * <p>对外 API 见 {@code com.seafood.product.application.ProductService}:
 * <ul>
 *   <li>{@code listSkus(productId)} — 查整张 SKU 列表(按 sortOrder 升序)</li>
 *   <li>{@code replaceSkus(productId, List<Sku>)} — 整张替换(ad-04 表单保存)</li>
 * </ul>
 */
public interface SkuRepository extends MongoRepository<ProductDocument, String> {

    /**
     * 按 productId 查 SKU 列表(嵌入形式,先查 product 再取 skus)。
     * 保留方法签名是为 Sprint 4 切独立 collection 时用。
     */
    default List<com.seafood.product.domain.Sku> findSkusByProductId(String productId) {
        return findById(productId)
                .map(ProductDocument::getSkus)
                .orElse(List.of());
    }
}
