package com.seafood.user.domain;

import java.time.Instant;

/**
 * 浏览足迹域对象(收藏 + 浏览足迹,design.md D1/D2)。
 *
 * <p>不是聚合根(没有需要保护的不变量,去重/裁剪逻辑在
 * {@code ProductViewService} 里,不在这里)——纯数据载体,{@code id} 由
 * MongoDB 自动生成,不在构造时校验。
 */
public record ProductView(String id, String userId, String productId, Instant viewedAt) {
}
