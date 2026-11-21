package com.example.insurance.paymentservice;

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

/** Layering, injection and money rules for payment-service (CLAUDE.md, PLAN 9). */
@AnalyzeClasses(
    packages = "com.example.insurance.paymentservice",
    importOptions = ImportOption.DoNotIncludeTests.class)
class PaymentServiceArchitectureTest {

  private static final String ROOT = "com.example.insurance.paymentservice..";

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
  static final ArchRule CONTROLLERS_USE_SERVICES_NOT_REPOSITORIES =
      noClasses()
          .that()
          .resideInAPackage("..controller..")
          .should()
          .dependOnClassesThat()
          .resideInAPackage("..repository..")
          .because("controllers go through services, which own the transactions");

  @ArchTest
  static final ArchRule DOMAIN_DOES_NOT_KNOW_THE_WEB =
      noClasses()
          .that()
          .resideInAPackage("..domain..")
          .should()
          .dependOnClassesThat()
          .resideInAnyPackage("..controller..", "..dto..", "org.springframework.web..")
          .because("the domain has no HTTP types");

  @ArchTest
  static final ArchRule NO_FLOATING_POINT_FIELDS =
      fields()
          .that()
          .areDeclaredInClassesThat()
          .resideInAPackage(ROOT)
          .should()
          .notHaveRawType(FLOATING_POINT)
          .because("money is BigDecimal, never double or float (CLAUDE.md)");

  @ArchTest
  static final ArchRule NO_FLOATING_POINT_PARAMETERS_OR_RESULTS =
      methods()
          .that()
          .areDeclaredInClassesThat()
          .resideInAPackage(ROOT)
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
