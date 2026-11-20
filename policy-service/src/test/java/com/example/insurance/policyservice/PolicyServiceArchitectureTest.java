package com.example.insurance.policyservice;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.fields;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.methods;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noFields;

import com.tngtech.archunit.base.DescribedPredicate;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaMethod;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchCondition;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.lang.ConditionEvents;
import com.tngtech.archunit.lang.SimpleConditionEvent;
import org.springframework.beans.factory.annotation.Autowired;

/** Layering, injection and money rules for policy-service (CLAUDE.md, ADR-001). */
@AnalyzeClasses(
    packages = "com.example.insurance.policyservice",
    importOptions = ImportOption.DoNotIncludeTests.class)
class PolicyServiceArchitectureTest {

  private static final String CATALOGUE = "com.example.insurance.policyservice.catalogue..";
  private static final String POLICY = "com.example.insurance.policyservice.policy..";
  private static final String[] MONEY_PACKAGES = {"..catalogue.rating..", "..catalogue.rules.."};

  private static final DescribedPredicate<JavaClass> FLOATING_POINT =
      DescribedPredicate.describe(
          "double or float",
          type ->
              type.isEquivalentTo(double.class)
                  || type.isEquivalentTo(float.class)
                  || type.isEquivalentTo(Double.class)
                  || type.isEquivalentTo(Float.class));

  @ArchTest
  static final ArchRule NO_FIELD_INJECTION =
      noFields()
          .should()
          .beAnnotatedWith(Autowired.class)
          .because("constructor injection only (CLAUDE.md Domain rules)");

  @ArchTest
  static final ArchRule CONTROLLERS_DO_NOT_TOUCH_PERSISTENCE =
      noClasses()
          .that()
          .resideInAPackage("..controller..")
          .should()
          .dependOnClassesThat()
          .resideInAnyPackage(
              "com.example.insurance..repository..", "com.example.insurance..domain..")
          .because("controllers use DTOs and services, never entities or repositories (CLAUDE.md)");

  @ArchTest
  static final ArchRule CATALOGUE_DOES_NOT_USE_POLICY =
      noClasses()
          .that()
          .resideInAPackage(CATALOGUE)
          .should()
          .dependOnClassesThat()
          .resideInAPackage(POLICY)
          .because("catalogue and policy talk only through interfaces (ADR-001)");

  @ArchTest
  static final ArchRule POLICY_DOES_NOT_USE_CATALOGUE_INTERNALS =
      noClasses()
          .that()
          .resideInAPackage(POLICY)
          .should()
          .dependOnClassesThat()
          .resideInAnyPackage("..catalogue.domain..", "..catalogue.repository..")
          .because("policy never touches catalogue entities or tables (ADR-001)")
          .allowEmptyShould(true);

  @ArchTest
  static final ArchRule NO_FLOATING_POINT_FIELDS =
      fields()
          .that()
          .areDeclaredInClassesThat()
          .resideInAnyPackage(MONEY_PACKAGES)
          .should()
          .notHaveRawType(FLOATING_POINT)
          .because("money is BigDecimal, never double or float (CLAUDE.md)")
          .allowEmptyShould(true);

  @ArchTest
  static final ArchRule NO_FLOATING_POINT_PARAMETERS_OR_RESULTS =
      methods()
          .that()
          .areDeclaredInClassesThat()
          .resideInAnyPackage(MONEY_PACKAGES)
          .should(notUseFloatingPoint())
          .because("money is BigDecimal, never double or float (CLAUDE.md)");

  private static ArchCondition<JavaMethod> notUseFloatingPoint() {
    return new ArchCondition<>("not take or return double or float") {
      @Override
      public void check(JavaMethod method, ConditionEvents events) {
        boolean bad =
            FLOATING_POINT.test(method.getRawReturnType())
                || method.getRawParameterTypes().stream().anyMatch(FLOATING_POINT);
        if (bad) {
          events.add(
              SimpleConditionEvent.violated(method, method.getFullName() + " uses floating point"));
        }
      }
    };
  }
}
