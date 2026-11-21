package com.example.insurance.claimservice;

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

/** Layering, injection and money rules for claim-service (CLAUDE.md, PLAN 9). */
@AnalyzeClasses(
    packages = "com.example.insurance.claimservice",
    importOptions = ImportOption.DoNotIncludeTests.class)
class ClaimServiceArchitectureTest {

  private static final String ROOT = "com.example.insurance.claimservice..";

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
          .because("the read model must not depend on HTTP shapes");

  @ArchTest
  static final ArchRule NO_DOUBLE_OR_FLOAT_FIELDS =
      noFields()
          .should()
          .haveRawType(FLOATING_POINT)
          .because("money is BigDecimal, never double or float (CLAUDE.md)");

  @ArchTest
  static final ArchRule NO_DOUBLE_OR_FLOAT_PARAMETERS =
      methods()
          .that()
          .areDeclaredInClassesThat()
          .resideInAPackage(ROOT)
          .should(notTakeOrReturnFloatingPoint())
          .because("money is BigDecimal, never double or float (CLAUDE.md)");

  private static ArchCondition<JavaMethod> notTakeOrReturnFloatingPoint() {
    return new ArchCondition<>("not take or return double or float") {
      @Override
      public void check(JavaMethod method, ConditionEvents events) {
        boolean floating =
            FLOATING_POINT.test(method.getRawReturnType())
                || method.getRawParameterTypes().stream().anyMatch(FLOATING_POINT::test);
        if (floating) {
          events.add(SimpleConditionEvent.violated(method, method.getFullName() + " uses a float"));
        }
      }
    };
  }
}
