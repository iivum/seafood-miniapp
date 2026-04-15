package com.seafood.user.infrastructure.persistence;

import org.springframework.context.annotation.Configuration;
import org.springframework.data.mongodb.config.EnableMongoAuditing;

/**
 * MongoDB auditing configuration.
 * Separated from UserApplication to allow @WebMvcTest slice tests
 * to run without loading full MongoDB context.
 */
@Configuration
@EnableMongoAuditing
public class MongoAuditingConfig {
}
