package com.seafood.order.infrastructure.config;

import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.mongodb.config.EnableMongoAuditing;

/**
 * Order service configuration class.
 *
 * <p>Enables:</p>
 * <ul>
 *   <li>Feign client support for inter-service communication</li>
 *   <li>MongoDB auditing for automatic timestamp management</li>
 * </ul>
 *
 * @see com.seafood.order.infrastructure.persistence.MongoOrderRepository
 */
@Configuration
@EnableFeignClients
@EnableMongoAuditing
public class OrderConfig {
}
