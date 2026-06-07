package com.seafood.architecture;

import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchCondition;
import com.tngtech.archunit.lang.ConditionEvents;
import com.tngtech.archunit.lang.SimpleConditionEvent;
import com.tngtech.archunit.lang.ArchRule;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RestController;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

/**
 * DDD layering guardrails. Runs as part of the default {@code gradle test} task.
 *
 * <p>Four rules:
 * <ol>
 *   <li>api → infra forbidden (preserves re-split option, design §1.3)</li>
 *   <li>bff → infra forbidden (BFF composes ApplicationServices only)</li>
 *   <li>domain → org.springframework.* forbidden except for the two data-mapping annotations
 *       used by {@code @Document} persistence entities</li>
 *   <li>controllers must not hold a {@code *Repository} field or constructor parameter</li>
 * </ol>
 *
 * <p>If a future rule needs an allow-list, add it as a named local variable with a
 * comment explaining the historical reason.
 */
@AnalyzeClasses(
        packages = "com.seafood",
        importOptions = ImportOption.DoNotIncludeTests.class)
class ArchitectureTest {

    @ArchTest
    static final ArchRule api_must_not_depend_on_infra =
            noClasses()
                    .that().resideInAPackage("..api..")
                    .should().dependOnClassesThat().resideInAPackage("..infra..")
                    .because("api layer may only call application services (design §1.3)");

    @ArchTest
    static final ArchRule bff_must_not_depend_on_infra =
            noClasses()
                    .that().resideInAPackage("..bff..")
                    .should().dependOnClassesThat().resideInAPackage("..infra..")
                    .because("BFF composes ApplicationServices across modules, never infra directly");

    @ArchTest
    static final ArchRule domain_must_stay_framework_agnostic =
            noClasses()
                    .that().resideInAPackage("..domain..")
                    .should().dependOnClassesThat().resideInAPackage("org.springframework..")
                    .because("domain layer must be JVM-pure; no Spring framework dependencies "
                            + "(mapping concerns belong in infra via @Document / @Id)")
                    .allowEmptyShould(true);

    @ArchTest
    static final ArchRule controllers_must_not_hold_repository =
            classes()
                    .that().areAnnotatedWith(RestController.class)
                    .or().areAnnotatedWith(Controller.class)
                    .should(notHaveRepositoryDependency())
                    .because("controllers may only depend on services, never on repositories");

    private static ArchCondition<JavaClass> notHaveRepositoryDependency() {
        return new ArchCondition<>("not have a field or constructor parameter of type *Repository") {
            @Override
            public void check(JavaClass item, ConditionEvents events) {
                // getAllInvolvedRawTypes() walks generic type arguments so wrapped types
                // like List<ProductRepository> or Optional<ProductRepository> are also caught.
                boolean bad = item.getAllFields().stream()
                        .flatMap(f -> f.getAllInvolvedRawTypes().stream())
                        .anyMatch(t -> t.getName().endsWith("Repository"));
                if (!bad) {
                    bad = item.getConstructors().stream()
                            .flatMap(c -> c.getParameterTypes().stream())
                            .flatMap(t -> t.getAllInvolvedRawTypes().stream())
                            .anyMatch(t -> t.getName().endsWith("Repository"));
                }
                if (bad) {
                    events.add(SimpleConditionEvent.violated(item,
                            item.getName() + " depends on a *Repository type"));
                }
            }
        };
    }
}
