package com.seafood.product.application;

import com.seafood.product.api.dto.ProductStatsResponse;
import com.seafood.product.domain.ProductStatus;
import com.seafood.product.infra.ProductDocument;
import com.seafood.product.infra.ProductRepository;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.aggregation.Aggregation;
import org.springframework.data.mongodb.core.aggregation.AggregationResults;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.springframework.data.mongodb.core.aggregation.Aggregation.group;
import static org.springframework.data.mongodb.core.aggregation.Aggregation.newAggregation;
import static org.springframework.data.mongodb.core.query.Criteria.where;

/**
 * 商品统计 / 聚合查询(参见 design.md §5.1 GET /api/admin/products/stats)。
 * 用 MongoTemplate aggregation 跑 group-by-category。
 */
@Service
public class ProductQueryService {

    private final ProductRepository repo;
    private final MongoTemplate mongo;

    public ProductQueryService(ProductRepository repo, MongoTemplate mongo) {
        this.repo = repo;
        this.mongo = mongo;
    }

    public ProductStatsResponse stats() {
        long total = repo.count();
        long onSale = repo.countByStatus(ProductStatus.ACTIVE);
        long outOfStock = repo.countByStock(0);

        Map<String, Long> byCategory = new LinkedHashMap<>();
        Aggregation agg = newAggregation(
                group("category").count().as("count"),
                Aggregation.project("count").and("_id").as("category"));
        AggregationResults<CountByCategory> results =
                mongo.aggregate(agg, "products", CountByCategory.class);
        for (CountByCategory row : results.getMappedResults()) {
            byCategory.put(row.category, row.count);
        }
        return new ProductStatsResponse(total, onSale, outOfStock, byCategory);
    }

    /** group-by 投影形态,字段顺序与上面 project 对齐。 */
    public record CountByCategory(String category, long count) {
    }
}
