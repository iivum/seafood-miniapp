package com.seafood.order.infrastructure.persistence;

import com.seafood.order.domain.model.Cart;
import com.seafood.order.domain.model.CartRepository;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * MongoDB implementation of CartRepository
 * Handles cart persistence operations using Spring Data MongoDB
 */
@Repository
public class MongoCartRepository implements CartRepository {

    private final MongoTemplate mongoTemplate;
    private static final String COLLECTION_NAME = "carts";
    private static final String USER_ID_FIELD = "userId";
    private static final String ITEMS_FIELD = "items";
    private static final String ITEMS_PRODUCT_ID_FIELD = "items.productId";

    public MongoCartRepository(MongoTemplate mongoTemplate) {
        this.mongoTemplate = mongoTemplate;
    }

    @Override
    public Cart save(Cart cart) {
        // Ensure the collection exists
        if (!mongoTemplate.collectionExists(COLLECTION_NAME)) {
            mongoTemplate.createCollection(COLLECTION_NAME);
        }

        // Check if cart already exists
        Query query = Query.query(Criteria.where(USER_ID_FIELD).is(cart.getUserId()));
        Cart existingCart = mongoTemplate.findOne(query, Cart.class, COLLECTION_NAME);

        if (existingCart != null) {
            // Update existing cart
            Update update = new Update()
                    .set("items", cart.getItems())
                    .set("totalPrice", cart.getTotalPrice())
                    .set("totalItems", cart.getTotalItems())
                    .set("selectedItems", cart.getSelectedItems());

            mongoTemplate.updateFirst(query, update, Cart.class, COLLECTION_NAME);
            return cart;
        } else {
            // Insert new cart
            return mongoTemplate.insert(cart, COLLECTION_NAME);
        }
    }

    @Override
    public Optional<Cart> findByUserId(String userId) {
        Query query = Query.query(Criteria.where(USER_ID_FIELD).is(userId));
        Cart cart = mongoTemplate.findOne(query, Cart.class, COLLECTION_NAME);
        return Optional.ofNullable(cart);
    }

    @Override
    public void delete(Cart cart) {
        Query query = Query.query(Criteria.where(USER_ID_FIELD).is(cart.getUserId()));
        mongoTemplate.remove(query, Cart.class, COLLECTION_NAME);
    }

    @Override
    public void deleteAll() {
        mongoTemplate.dropCollection(COLLECTION_NAME);
    }

    @Override
    public long count() {
        return mongoTemplate.getCollection(COLLECTION_NAME).countDocuments();
    }

    @Override
    public List<Cart> findAll() {
        return mongoTemplate.findAll(Cart.class, COLLECTION_NAME);
    }

    @Override
    public List<Cart> findByUserIdIn(List<String> userIds) {
        Query query = Query.query(Criteria.where(USER_ID_FIELD).in(userIds));
        return mongoTemplate.find(query, Cart.class, COLLECTION_NAME);
    }

    @Override
    public Optional<Cart> findById(String id) {
        return Optional.ofNullable(mongoTemplate.findById(id, Cart.class, COLLECTION_NAME));
    }

    @Override
    public void deleteByUserId(String userId) {
        Query query = Query.query(Criteria.where(USER_ID_FIELD).is(userId));
        mongoTemplate.remove(query, COLLECTION_NAME);
    }

    @Override
    public List<Cart> findCartsWithItem(String productId) {
        Query query = Query.query(
            Criteria.where(ITEMS_FIELD + ".productId").is(productId)
        );
        return mongoTemplate.find(query, Cart.class, COLLECTION_NAME);
    }

    @Override
    public List<Cart> findCartsBySelectedProduct(String productId) {
        Query query = Query.query(
            Criteria.where("selectedItems").is(productId)
        );
        return mongoTemplate.find(query, Cart.class, COLLECTION_NAME);
    }

    @Override
    public List<Cart> findCartsCreatedBefore(java.time.LocalDateTime dateTime) {
        Query query = Query.query(
            Criteria.where("createdAt").lt(java.sql.Timestamp.valueOf(dateTime))
        );
        return mongoTemplate.find(query, Cart.class, COLLECTION_NAME);
    }

    @Override
    public long countByUserId(String userId) {
        Query query = Query.query(Criteria.where(USER_ID_FIELD).is(userId));
        return mongoTemplate.count(query, COLLECTION_NAME);
    }
}