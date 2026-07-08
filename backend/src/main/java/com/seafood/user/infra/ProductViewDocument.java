package com.seafood.user.infra;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;

/**
 * product_views collection(收藏 + 浏览足迹,design.md D1)。
 *
 * <p>{@code userId+productId} 唯一约束是手写 critical 索引(见
 * {@code MongoIndexInitializer}),不用 {@code @CompoundIndex} 注解——这个仓库的
 * 惯例是:annotation-derived 索引失败仅 warn(性能类),而这个唯一约束是
 * upsert/去重语义的正确性前提(同一商品反复查看只保留最新一条),缺失会让
 * "去重刷新 viewedAt" 退化成"每次都新增一条",裁剪到 100 条的语义也会跟着错——
 * 所以走 {@code ensureCritical},同 {@code users.openId} 唯一索引一样。
 */
@Document(collection = "product_views")
public class ProductViewDocument {

    @Id
    private String id;

    private String userId;
    private String productId;
    private Instant viewedAt;

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getUserId() { return userId; }
    public void setUserId(String userId) { this.userId = userId; }

    public String getProductId() { return productId; }
    public void setProductId(String productId) { this.productId = productId; }

    public Instant getViewedAt() { return viewedAt; }
    public void setViewedAt(Instant viewedAt) { this.viewedAt = viewedAt; }
}
