package com.seafood.architecture;

import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaMethod;
import com.tngtech.archunit.core.domain.JavaMethodCall;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchCondition;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.lang.ConditionEvents;
import com.tngtech.archunit.lang.SimpleConditionEvent;
import com.seafood.shared.security.AdminRateLimitFilter;
import com.seafood.shared.security.SecurityHeadersFilter;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.methods;

/**
 * Sprint 2 §2.3 — 强制 HTTP 安全响应头只由 {@link SecurityHeadersFilter} 写入。
 *
 * <p>策略:扫描所有方法,任何方法若包含目标为
 * {@code HttpServletResponse#setHeader(String, String)} 的方法调用,且所在类不是
 * 白名单中的 filter,即为违规。
 *
 * <p>ArchUnit 字节码分析无法读出 setHeader 的字符串字面值,所以本规则用 "白名单 +
 * owner 检查" 近似策略;白名单:
 * <ul>
 *   <li>{@link SecurityHeadersFilter} — 6 个基线安全头的唯一生产者</li>
 *   <li>{@link AdminRateLimitFilter} — 写 {@code Retry-After} (非安全头)</li>
 * </ul>
 * 任何其它类调用 setHeader 即视为"想在 filter 链里塞头",触发 review。
 */
@AnalyzeClasses(
        packages = "com.seafood",
        importOptions = ImportOption.DoNotIncludeTests.class)
class SecurityHeaderArchitectureTest {

    @ArchTest
    static final ArchRule onlySecurityHeadersFilter_may_call_setHeader =
            methods()
                    .that().areDeclaredInClassesThat().areNotAssignableTo(SecurityHeadersFilter.class)
                    .and().areDeclaredInClassesThat().areNotAssignableTo(AdminRateLimitFilter.class)
                    .should(notCallHttpServletResponseSetHeader())
                    .because("baseline security headers must only be written by "
                            + "com.seafood.shared.security.SecurityHeadersFilter; "
                            + "centralize new headers in SecurityHeadersProperties so they "
                            + "stay auditable")
                    .allowEmptyShould(true);

    private static ArchCondition<JavaMethod> notCallHttpServletResponseSetHeader() {
        return new ArchCondition<>("not call HttpServletResponse.setHeader") {
            @Override
            public void check(JavaMethod method, ConditionEvents events) {
                for (JavaMethodCall call : method.getMethodCallsFromSelf()) {
                    JavaClass owner = call.getTarget().getOwner();
                    if (owner == null) {
                        continue;
                    }
                    String ownerName = owner.getName();
                    if (("jakarta.servlet.http.HttpServletResponse".equals(ownerName)
                            || "javax.servlet.http.HttpServletResponse".equals(ownerName))
                            && "setHeader".equals(call.getTarget().getName())) {
                        events.add(SimpleConditionEvent.violated(method,
                                method.getOwner().getName() + "#" + method.getName()
                                        + " calls HttpServletResponse.setHeader; baseline security "
                                        + "headers must only be written by SecurityHeadersFilter"));
                    }
                }
            }
        };
    }
}
