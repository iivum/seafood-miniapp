package com.seafood.order.application;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

/**
 * OpenSpec setup-observability-stack PR #3 / task 3.1.2 — {@link OrderMetrics#bucketize} 单元测试。
 *
 * <p>覆盖(design §ADR-OQ3 几何 4 档分桶):
 * <ol>
 *   <li>4 个区间左右边界都验:</li>
 *   <ul>
 *     <li>{@code lt100}:0 / 50 / 99.99</li>
 *     <li>{@code 100to500}:100 / 250 / 499.99</li>
 *     <li>{@code 500to2000}:500 / 1000 / 1999.99</li>
 *     <li>{@code gte2000}:2000 / 5000 / 999999</li>
 *   </ul>
 *   <li>负数 → {@link IllegalArgumentException}</li>
 *   <li>{@code null} → {@link NullPointerException}</li>
 *   <li>ALL_BUCKETS 白名单完整性</li>
 * </ol>
 */
class OrderMetricsTest {

    @ParameterizedTest(name = "{0} → {1}")
    @CsvSource({
            // lt100 区间
            "0,         lt100",
            "0.01,      lt100",
            "50,        lt100",
            "99.99,     lt100",
            // 100to500 区间
            "100,       100to500",
            "100.01,    100to500",
            "250,       100to500",
            "499.99,    100to500",
            // 500to2000 区间
            "500,       500to2000",
            "500.01,    500to2000",
            "1000,      500to2000",
            "1999.99,   500to2000",
            // gte2000 区间
            "2000,      gte2000",
            "2000.01,   gte2000",
            "999999.99, gte2000",
    })
    @DisplayName("bucketize:几何 4 档分桶(覆盖 4 区间 16 个值,含每个区间左右边界)")
    void bucketize_classifiesIntoOneOfFourBuckets(String amount, String expected) {
        assertThat(OrderMetrics.bucketize(new BigDecimal(amount)))
                .as("amount=%s 应映射到 bucket=%s", amount, expected)
                .isEqualTo(expected);
    }

    @ParameterizedTest
    @ValueSource(strings = {"-0.01", "-1", "-100", "-9999.99"})
    @DisplayName("bucketize:负数抛 IllegalArgumentException")
    void bucketize_rejectsNegativeAmount(String amount) {
        assertThatExceptionOfType(IllegalArgumentException.class)
                .isThrownBy(() -> OrderMetrics.bucketize(new BigDecimal(amount)))
                .withMessageContaining("non-negative");
    }

    @Test
    @DisplayName("bucketize:null 抛 NullPointerException")
    void bucketize_rejectsNull() {
        assertThatNullPointerException()
                .isThrownBy(() -> OrderMetrics.bucketize(null))
                .withMessageContaining("amount");
    }

    @Test
    @DisplayName("ALL_BUCKETS:白名单 4 项,正好覆盖 bucketize 的所有可能输出")
    void allBucketsIsExhaustiveWhitelist() {
        assertThat(OrderMetrics.ALL_BUCKETS)
                .containsExactlyInAnyOrder(
                        OrderMetrics.BUCKET_LT_100,
                        OrderMetrics.BUCKET_100_TO_500,
                        OrderMetrics.BUCKET_500_TO_2000,
                        OrderMetrics.BUCKET_GTE_2000);
    }

    @Test
    @DisplayName("bucketize:白名单中每个值都能被 bucketize 命中(无死代码)")
    void allBucketsAreReachableFromBucketize() {
        for (String bucket : OrderMetrics.ALL_BUCKETS) {
            BigDecimal sample = switch (bucket) {
                case OrderMetrics.BUCKET_LT_100      -> new BigDecimal("50");
                case OrderMetrics.BUCKET_100_TO_500  -> new BigDecimal("250");
                case OrderMetrics.BUCKET_500_TO_2000 -> new BigDecimal("1000");
                case OrderMetrics.BUCKET_GTE_2000    -> new BigDecimal("5000");
                default -> throw new AssertionError("unknown bucket: " + bucket);
            };
            assertThat(OrderMetrics.bucketize(sample))
                    .as("sample %s for bucket %s", sample, bucket)
                    .isEqualTo(bucket);
        }
    }
}
