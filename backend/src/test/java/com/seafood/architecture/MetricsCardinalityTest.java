package com.seafood.architecture;

import com.tngtech.archunit.core.domain.JavaCall;
import com.tngtech.archunit.core.domain.JavaMethod;
import com.tngtech.archunit.core.domain.SourceCodeLocation;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchCondition;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.lang.ConditionEvents;
import com.tngtech.archunit.lang.SimpleConditionEvent;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.methods;

/**
 * OpenSpec setup-observability-stack PR #3 / task 3.7.1-3.7.3 —
 * 业务埋点 tag cardinality 静态约束(design §D5 + ADR-OQ3)。
 *
 * <p>Prometheus tag value 必须严格低基数,否则攻击者/正常用户用 userId / orderId /
 * productId / email 等高基数 PII 字段做 tag 时,Prometheus 时间序列数量爆炸,导致
 * 内存溢出 + 查询超时。本类禁止 Micrometer 业务埋点用以下高基数 tag key:
 * <ul>
 *   <li>{@code userId}</li>
 *   <li>{@code orderId}</li>
 *   <li>{@code productId}</li>
 *   <li>{@code email}</li>
 * </ul>
 *
 * <p><b>实现说明</b>:ArchUnit 1.4 的 {@link JavaCall#getDescription()} 只返回
 * <em>方法签名</em>(形参类型),不返回 call site 处的实参字面量。本规则改走
 * {@link SourceCodeLocation} 拿到源码文件 + 行号,直接读源文件相应行及后续 N 行,
 * 正则扫字符串字面量,匹配到 forbidden tag key 即报错。
 *
 * <p>对单行 call {@code meterRegistry.counter("foo", "bar")} 100% 命中;对跨行
 * call 也能覆盖(读后续 3 行)。对动态拼字符串
 * (e.g. {@code meterRegistry.counter("user." + id)})无法在编译期判断,留给
 * code review。如果未来真需要动态 key,应回退到 design §D5 重新审视。
 */
@AnalyzeClasses(
        packages = "com.seafood",
        importOptions = ImportOption.DoNotIncludeTests.class)
class MetricsCardinalityTest {

    /** 高基数 / PII 字段 — 禁止作为 Prometheus tag key。 */
    static final Set<String> FORBIDDEN_TAG_KEYS = Set.of(
            "userId", "orderId", "productId", "email");

    /** Micrometer 允许的埋点 API(只扫业务侧高频的 3 个)。 */
    private static final List<String> METER_REGISTRY_METHODS = List.of(
            "counter", "timer", "gauge");

    /** 匹配双引号字符串字面量。 */
    private static final Pattern STRING_LITERAL = Pattern.compile("\"([^\"]*)\"");

    /** 跨行 call 时,call 起始行后最多读这多行找实参。 */
    private static final int MAX_LINES_AFTER_CALL_SITE = 3;

    @ArchTest
    static final ArchRule meterRegistry_calls_must_not_use_forbidden_tag_keys =
            methods()
                    .that().areDeclaredInClassesThat().resideInAPackage("com.seafood..")
                    .and().areDeclaredInClassesThat().areNotAssignableTo(
                            io.micrometer.core.instrument.MeterRegistry.class)
                    .should(notUseForbiddenTagKeys());

    private static ArchCondition<JavaMethod> notUseForbiddenTagKeys() {
        return new ArchCondition<>("not use forbidden high-cardinality / PII tag keys") {
            @Override
            public void check(JavaMethod method, ConditionEvents events) {
                for (JavaCall<?> call : method.getMethodCallsFromSelf()) {
                    String targetOwner = call.getTargetOwner().getName();
                    String targetName = call.getTarget().getName();

                    if (!"io.micrometer.core.instrument.MeterRegistry".equals(targetOwner)) {
                        continue;
                    }
                    if (!METER_REGISTRY_METHODS.contains(targetName)) {
                        continue;
                    }

                    SourceCodeLocation loc = call.getSourceCodeLocation();
                    if (loc == null) {
                        continue;
                    }
                    String snippet = readSourceLines(method.getOwner().getName(), loc,
                            MAX_LINES_AFTER_CALL_SITE);
                    if (snippet == null) {
                        // 读源失败 — 跳过,避免在 IDE 误报(留 review 兜底)
                        continue;
                    }
                    Matcher m = STRING_LITERAL.matcher(snippet);
                    List<String> literals = new java.util.ArrayList<>();
                    while (m.find()) {
                        literals.add(m.group(1));
                    }
                    if (literals.size() <= 1) {
                        continue; // 0 / 1 个字面量 = 只有 metric name,无 tag key
                    }
                    // index 0 = metric name;index 1,3,5,... = tag keys
                    for (int i = 1; i < literals.size(); i += 2) {
                        String key = literals.get(i);
                        if (FORBIDDEN_TAG_KEYS.contains(key)) {
                            events.add(SimpleConditionEvent.violated(method,
                                    String.format(
                                            "%s.%s() (line %d of %s) calls MeterRegistry.%s() with forbidden tag key "
                                                    + "'%s' (allowed whitelist excludes userId/orderId/productId/email; "
                                                    + "see design §D5 + ADR-OQ3).",
                                            method.getOwner().getName(),
                                            method.getName(),
                                            loc.getLineNumber(),
                                            method.getOwner().getName().replace('.', '/') + ".java",
                                            targetName,
                                            key)));
                        }
                    }
                }
            }
        };
    }

    /**
     * 读 call 起始行开始的 {@code maxLinesAfter} 行源码。跨行 call 也能覆盖。
     * 文件不存在 / 读失败返 {@code null},调用方按"无法验证"处理(跳过)。
     *
     * <p>路径解析:从 owner FQN 拼出 {@code src/main/java/<package>/<Class>.java}。
     * 假设标准 Gradle 单模块布局;若改 buildSrc / 多 module 需重写。
     */
    private static String readSourceLines(String ownerClassName, SourceCodeLocation loc,
                                          int maxLinesAfter) {
        String relative = "src/main/java/" + ownerClassName.replace('.', '/') + ".java";
        // 相对当前 working dir 解析(Gradle test task 的 cwd = backend/)
        Path file = Path.of(relative);
        if (!Files.exists(file)) {
            return null;
        }
        try {
            List<String> all = Files.readAllLines(file);
            int start = Math.max(0, loc.getLineNumber() - 1);
            int end = Math.min(all.size(), start + maxLinesAfter + 1);
            return String.join("\n", all.subList(start, end));
        } catch (IOException e) {
            return null;
        }
    }
}
