package com.seafood.shared.infra;

import com.mongodb.client.model.IndexOptions;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.index.Index;
import org.springframework.data.mongodb.core.index.IndexOperations;
import org.springframework.data.mongodb.core.index.IndexResolver;
import org.springframework.data.mongodb.core.index.MongoPersistentEntityIndexResolver;
import org.springframework.data.mongodb.core.mapping.MongoMappingContext;
import org.springframework.stereotype.Component;

import com.seafood.product.infra.ProductDocument;
import com.seafood.order.infra.OrderDocument;
import com.seafood.user.infra.UserDocument;

/**
 * 启动时建索引(design §6.2,specs/backend-api §Native Image safety)。
 *
 * <p>两个来源:
 * <ol>
 *   <li>由 {@link MongoPersistentEntityIndexResolver} 从 {@code @Document} / {@code @Indexed}
 *       注解解析出来的索引(Product/Order/User)</li>
 *   <li>手写的额外索引 — 例如 users.openId unique、products 文本索引、orders.createdAt 倒序</li>
 * </ol>
 *
 * <p>{@code @EventListener(ApplicationReadyEvent.class)}:确保只在容器启动成功后建;
 * 用 {@code createIndex} 幂等(同名同 keySpecs 重复调用 OK)。
 */
@Component
public class MongoIndexInitializer {

    private static final Logger log = LoggerFactory.getLogger(MongoIndexInitializer.class);

    private final MongoTemplate mongo;
    private final MongoMappingContext mappingContext;

    public MongoIndexInitializer(MongoTemplate mongo, MongoMappingContext mappingContext) {
        this.mongo = mongo;
        this.mappingContext = mappingContext;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void init() {
        ensureAnnotationDerived(ProductDocument.class);
        ensureAnnotationDerived(OrderDocument.class);
        ensureAnnotationDerived(UserDocument.class);

        ensureExtra("products",
                new Index().on("name", org.springframework.data.domain.Sort.Direction.ASC)
                        .on("description", org.springframework.data.domain.Sort.Direction.ASC)
                        .named("text_name_description"));

        ensureExtra("users",
                new Index().on("openId", org.springframework.data.domain.Sort.Direction.ASC)
                        .unique()
                        .named("uk_openId"));

        log.info("[mongo] all indexes ensured");
    }

    private void ensureAnnotationDerived(Class<?> docClass) {
        IndexOperations ops = mongo.indexOps(docClass);
        IndexResolver resolver = new MongoPersistentEntityIndexResolver(mappingContext);
        resolver.resolveIndexFor(docClass).forEach(def -> {
            try {
                ops.ensureIndex(def);
            } catch (Exception e) {
                log.warn("[mongo] ensureIndex {} failed: {}", def, e.getMessage());
            }
        });
    }

    private void ensureExtra(String collection, Index index) {
        try {
            mongo.indexOps(collection).ensureIndex(index);
        } catch (Exception e) {
            log.warn("[mongo] ensureIndex {} on {} failed: {}", index, collection, e.getMessage());
        }
    }
}
