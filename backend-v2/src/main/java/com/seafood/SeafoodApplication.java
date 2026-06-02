package com.seafood;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.data.mongodb.repository.config.EnableMongoRepositories;

@SpringBootApplication
@ConfigurationPropertiesScan
@EnableMongoRepositories(basePackages = "com.seafood")
public class SeafoodApplication {
    public static void main(String[] args) {
        SpringApplication.run(SeafoodApplication.class, args);
    }
}
