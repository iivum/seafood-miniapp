package com.seafood.product.infra;

import com.seafood.product.domain.ProductStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.convert.converter.Converter;
import org.springframework.data.convert.ReadingConverter;

/**
 * fix-category-bad-status-500:products 集合里出现非法/未识别的 status 字符串时
 * （已知历史成因：手工 seed 误写 {@code INACTIVE}），Spring Data MongoDB 默认的
 * enum 转换在 document→entity 阶段直接抛 {@code IllegalArgumentException}，把
 * 单条坏数据放大成整个查询失败。本 converter 兜底：未识别值归一为
 * {@link ProductStatus#DISCONTINUED}（语义上"不在公开可见范围"，与该字段既有注释
 * 一致），记录 WARN 而不是让请求整体失败。
 */
@ReadingConverter
class ProductStatusReadConverter implements Converter<String, ProductStatus> {

    private static final Logger log = LoggerFactory.getLogger(ProductStatusReadConverter.class);

    @Override
    public ProductStatus convert(String source) {
        try {
            return ProductStatus.valueOf(source);
        } catch (IllegalArgumentException e) {
            log.warn("Unrecognized product status value '{}', treating as DISCONTINUED", source);
            return ProductStatus.DISCONTINUED;
        }
    }
}
