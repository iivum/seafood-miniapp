package com.seafood.order.infra;

import com.seafood.order.domain.Cart;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface CartMongoRepository extends MongoRepository<Cart, String> {}
