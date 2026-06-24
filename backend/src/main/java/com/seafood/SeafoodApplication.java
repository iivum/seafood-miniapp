package com.seafood;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * 单 Spring Boot 入口 - 收敛 7 个微服务到一个进程(参见 refactor-rust-rebuild-frontend)。
 * 业务包按 bounded context 划分:product / order / user / bff.admin / shared。
 */
@SpringBootApplication
@EnableScheduling
public class SeafoodApplication {

    public static void main(String[] args) {
        SpringApplication.run(SeafoodApplication.class, args);
    }
}
