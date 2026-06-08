package com.seafood.shared.observability;

import com.fasterxml.uuid.Generators;
import com.fasterxml.uuid.impl.TimeBasedEpochGenerator;

import java.util.UUID;

/**
 * UUID v7 生成器抽象(OpenSpec setup-observability-stack ADR-OQ1)。
 *
 * <p>默认实现包装 FasterXML JUG 5.1.0 的 {@link Generators#timeBasedEpochGenerator()},
 * 该生成器为线程安全单例,无反射,体积约 50 KB,GraalVM Native 友好。
 *
 * <p>引入此接口的目的:
 * <ul>
 *   <li>单元测试可注入固定 UUID 验证串联(后续 PR 决策可换 mock)</li>
 *   <li>若 JDK 后续版本内置 v7 API,可一行替换默认实现,filter 代码不变</li>
 *   <li>符合"跨切关注点用接口隔离副作用"的项目惯例</li>
 * </ul>
 */
public interface RequestIdGenerator {

    /**
     * 产生一个新的 UUID v7。
     *
     * @return 时间有序的 UUID(高位 48 bit 为 unix timestamp ms)
     */
    UUID next();

    /**
     * 默认实现 — 直接代理到 JUG 的 {@link TimeBasedEpochGenerator#generate()}。
     * 该生成器内部维护单调时钟,线程安全,无需每个调用方做同步。
     */
    final class TimeBasedEpoch implements RequestIdGenerator {

        private static final TimeBasedEpochGenerator INTERNAL = Generators.timeBasedEpochGenerator();

        @Override
        public UUID next() {
            return INTERNAL.generate();
        }
    }
}
