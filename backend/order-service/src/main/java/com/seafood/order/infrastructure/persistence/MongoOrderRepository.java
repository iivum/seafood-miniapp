package com.seafood.order.infrastructure.persistence;

import com.seafood.order.domain.model.Order;
import com.seafood.order.domain.model.OrderRepository;
import com.seafood.order.domain.model.OrderStatus;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Date;
import java.util.List;
import java.util.Optional;

/**
 * MongoDB implementation of OrderRepository
 * Provides data access operations for Order aggregate using Spring Data MongoDB
 */
@Repository
public class MongoOrderRepository implements OrderRepository {

    private final MongoTemplate mongoTemplate;
    private static final String COLLECTION_NAME = "orders";
    private static final String USER_ID_FIELD = "userId";
    private static final String ORDER_NUMBER_FIELD = "orderNumber";
    private static final String STATUS_FIELD = "status";
    private static final String CREATED_AT_FIELD = "createdAt";
    private static final String TOTAL_PRICE_FIELD = "totalPrice";

    public MongoOrderRepository(MongoTemplate mongoTemplate) {
        this.mongoTemplate = mongoTemplate;
    }

    @Override
    public Order save(Order order) {
        // Ensure collection exists
        if (!mongoTemplate.collectionExists(COLLECTION_NAME)) {
            mongoTemplate.createCollection(COLLECTION_NAME);
        }

        // Create indexes for frequently queried fields
        createIndexes();

        // Save or update the order
        return mongoTemplate.save(order, COLLECTION_NAME);
    }

    @Override
    public Optional<Order> findById(String id) {
        Query query = Query.query(Criteria.where("_id").is(id));
        Order order = mongoTemplate.findOne(query, Order.class, COLLECTION_NAME);
        return Optional.ofNullable(order);
    }

    @Override
    public List<Order> findByUserId(String userId) {
        Query query = Query.query(Criteria.where(USER_ID_FIELD).is(userId))
                .with(org.springframework.data.domain.Sort.by(org.springframework.data.domain.Sort.Direction.DESC, CREATED_AT_FIELD));
        return mongoTemplate.find(query, Order.class, COLLECTION_NAME);
    }

    @Override
    public Optional<Order> findByOrderNumber(String orderNumber) {
        Query query = Query.query(Criteria.where(ORDER_NUMBER_FIELD).is(orderNumber));
        Order order = mongoTemplate.findOne(query, Order.class, COLLECTION_NAME);
        return Optional.ofNullable(order);
    }

    @Override
    public List<Order> findByStatus(OrderStatus status) {
        Query query = Query.query(Criteria.where(STATUS_FIELD).is(status))
                .with(org.springframework.data.domain.Sort.by(org.springframework.data.domain.Sort.Direction.DESC, CREATED_AT_FIELD));
        return mongoTemplate.find(query, Order.class, COLLECTION_NAME);
    }

    @Override
    public List<Order> findByUserIdAndStatus(String userId, OrderStatus status) {
        Query query = Query.query(Criteria.where(USER_ID_FIELD).is(userId)
                .and(STATUS_FIELD).is(status))
                .with(org.springframework.data.domain.Sort.by(org.springframework.data.domain.Sort.Direction.DESC, CREATED_AT_FIELD));
        return mongoTemplate.find(query, Order.class, COLLECTION_NAME);
    }

    @Override
    public void delete(Order order) {
        Query query = Query.query(Criteria.where("_id").is(order.getId()));
        mongoTemplate.remove(query, Order.class, COLLECTION_NAME);
    }

    @Override
    public void deleteByUserId(String userId) {
        Query query = Query.query(Criteria.where(USER_ID_FIELD).is(userId));
        mongoTemplate.remove(query, COLLECTION_NAME);
    }

    @Override
    public long countByUserId(String userId) {
        Query query = Query.query(Criteria.where(USER_ID_FIELD).is(userId));
        return mongoTemplate.count(query, COLLECTION_NAME);
    }

    @Override
    public long countByStatus(OrderStatus status) {
        Query query = Query.query(Criteria.where(STATUS_FIELD).is(status));
        return mongoTemplate.count(query, COLLECTION_NAME);
    }

    @Override
    public List<Order> findOrdersCreatedBetween(LocalDateTime startDate, LocalDateTime endDate) {
        Date start = Date.from(startDate.atZone(ZoneId.systemDefault()).toInstant());
        Date end = Date.from(endDate.atZone(ZoneId.systemDefault()).toInstant());

        Query query = Query.query(Criteria.where(CREATED_AT_FIELD).gte(start).lte(end))
                .with(org.springframework.data.domain.Sort.by(org.springframework.data.domain.Sort.Direction.DESC, CREATED_AT_FIELD));
        return mongoTemplate.find(query, Order.class, COLLECTION_NAME);
    }

    @Override
    public List<Order> findOrdersWithTotalPriceGreaterThan(double minTotalPrice) {
        Query query = Query.query(Criteria.where(TOTAL_PRICE_FIELD).gt(minTotalPrice))
                .with(org.springframework.data.domain.Sort.by(org.springframework.data.domain.Sort.Direction.DESC, TOTAL_PRICE_FIELD));
        return mongoTemplate.find(query, Order.class, COLLECTION_NAME);
    }

    @Override
    public boolean updateOrderStatus(String orderId, OrderStatus newStatus) {
        Query query = Query.query(Criteria.where("_id").is(orderId));
        Update update = new Update().set(STATUS_FIELD, newStatus);

        // Add status change to order history
        Order order = findById(orderId).orElse(null);
        if (order != null) {
            order.setStatus(newStatus);
            mongoTemplate.save(order, COLLECTION_NAME);
            return true;
        }
        return false;
    }

    @Override
    public List<Order> findAll() {
        return mongoTemplate.findAll(Order.class, COLLECTION_NAME);
    }

    @Override
    public void deleteAll() {
        mongoTemplate.dropCollection(COLLECTION_NAME);
    }

    private void createIndexes() {
        // Create compound index for userId and status queries
        mongoTemplate.indexOps(COLLECTION_NAME).ensureIndex(
                new org.springframework.data.mongodb.core.index.Index()
                        .on(USER_ID_FIELD, org.springframework.data.domain.Sort.Direction.ASC)
                        .on(STATUS_FIELD, org.springframework.data.domain.Sort.Direction.ASC)
        );

        // Create index for orderNumber queries
        mongoTemplate.indexOps(COLLECTION_NAME).ensureIndex(
                new org.springframework.data.mongodb.core.index.Index()
                        .on(ORDER_NUMBER_FIELD, org.springframework.data.domain.Sort.Direction.ASC)
                        .unique()
        );

        // Create index for createdAt queries (for date range searches)
        mongoTemplate.indexOps(COLLECTION_NAME).ensureIndex(
                new org.springframework.data.mongodb.core.index.Index()
                        .on(CREATED_AT_FIELD, org.springframework.data.domain.Sort.Direction.DESC)
        );

        // Create index for totalPrice queries (for price range searches)
        mongoTemplate.indexOps(COLLECTION_NAME).ensureIndex(
                new org.springframework.data.mongodb.core.index.Index()
                        .on(TOTAL_PRICE_FIELD, org.springframework.data.domain.Sort.Direction.DESC)
        );
    }
}