package com.seafood.order.infra;

import com.seafood.order.domain.OrderStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.time.Instant;
import java.util.List;

public interface OrderRepository extends MongoRepository<OrderDocument, String> {

    Page<OrderDocument> findByUserId(String userId, Pageable pageable);

    Page<OrderDocument> findByStatus(OrderStatus status, Pageable pageable);

    long countByCreatedAtGreaterThanEqual(Instant from);

    List<OrderDocument> findTop500ByOrderByCreatedAtDesc();
}
