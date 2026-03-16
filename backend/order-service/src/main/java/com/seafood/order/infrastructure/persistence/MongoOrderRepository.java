package com.seafood.order.infrastructure.persistence;

import com.seafood.order.domain.model.Order;
import com.seafood.order.domain.model.OrderRepository;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface MongoOrderRepository extends OrderRepository, MongoRepository<Order, String> {
    List<Order> findByUserId(String userId);
}
