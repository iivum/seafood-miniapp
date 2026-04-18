package com.seafood.order;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.data.mongodb.config.EnableMongoAuditing;

/**
 * Order Service Spring Boot application entry point.
 *
 * <p>This microservice handles order management including:</p>
 * <ul>
 *   <li>Order creation and lifecycle management</li>
 *   <li>Shopping cart operations</li>
 *   <li>Shipping address management</li>
 *   <li>Payment processing integration</li>
 *   <li>Inventory synchronization</li>
 * </ul>
 *
 * <p>The service registers with Eureka for service discovery and
 * uses MongoDB for persistence.</p>
 *
 * @see <a href="https://spring.io/projects/spring-cloud-openfeign">Spring Cloud OpenFeign</a>
 * @see <a href="https://spring.io/projects/spring-data-mongodb">Spring Data MongoDB</a>
 */
@SpringBootApplication
@EnableDiscoveryClient
public class OrderApplication {
    public static void main(String[] args) {
        SpringApplication.run(OrderApplication.class, args);
    }
}
