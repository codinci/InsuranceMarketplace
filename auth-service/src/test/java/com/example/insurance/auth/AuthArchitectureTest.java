package com.example.insurance.auth;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noFields;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;
import org.springframework.beans.factory.annotation.Autowired;

/** Layering and injection rules for auth-service (CLAUDE.md Domain rules, PLAN 16.1). */
@AnalyzeClasses(
    packages = "com.example.insurance.auth",
    importOptions = ImportOption.DoNotIncludeTests.class)
class AuthArchitectureTest {

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
          .resideInAnyPackage("..repository..", "..domain..")
          .because("controllers use DTOs and services, never entities or repositories (CLAUDE.md)");

  @ArchTest
  static final ArchRule DOMAIN_DOES_NOT_KNOW_THE_WEB_LAYER =
      noClasses()
          .that()
          .resideInAPackage("..domain..")
          .should()
          .dependOnClassesThat()
          .resideInAnyPackage("..controller..", "..dto..")
          .because("entities must not depend on the API shape");
}
