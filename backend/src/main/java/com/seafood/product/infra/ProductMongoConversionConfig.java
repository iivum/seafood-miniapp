package com.seafood.product.infra;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.mongodb.core.convert.MongoCustomConversions;

import java.util.List;

/**
 * fix-category-bad-status-500:注册 {@link ProductStatusReadConverter}，让
 * products 集合里出现的非法 status 值不再让 document→entity 转换整体抛异常。
 * 目前仓库里唯一的 {@code MongoCustomConversions} bean。
 */
@Configuration
public class ProductMongoConversionConfig {

    @Bean
    public MongoCustomConversions mongoCustomConversions() {
        return new MongoCustomConversions(List.of(new ProductStatusReadConverter()));
    }
}
