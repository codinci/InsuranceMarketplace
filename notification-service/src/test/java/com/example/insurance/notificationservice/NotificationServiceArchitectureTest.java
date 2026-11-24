package com.example.insurance.notificationservice;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noFields;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;
import org.springframework.beans.factory.annotation.Autowired;

/** Layering and injection rules for notification-service (CLAUDE.md, PLAN 9). */
@AnalyzeClasses(
    packages = "com.example.insurance.notificationservice",
    importOptions = ImportOption.DoNotIncludeTests.class)
class NotificationServiceArchitectureTest {

  @ArchTest
  static final ArchRule NO_FIELD_INJECTION =
      noFields()
          .should()
          .beAnnotatedWith(Autowired.class)
          .because("constructor injection only (CLAUDE.md Domain rules)");

  @ArchTest
  static final ArchRule LISTENERS_USE_SERVICES_NOT_REPOSITORIES =
      noClasses()
          .that()
          .resideInAPackage("..messaging..")
          .should()
          .dependOnClassesThat()
          .resideInAPackage("..repository..")
          .because("listeners go through services, which own the transactions");

  @ArchTest
  static final ArchRule NO_PUBLIC_API =
      noClasses()
          .should()
          .beAnnotatedWith("org.springframework.web.bind.annotation.RestController")
          .orShould()
          .beAnnotatedWith("org.springframework.stereotype.Controller")
          .because("notification-service has no public API (PLAN 3); a read endpoint is parked");

  @ArchTest
  static final ArchRule DOMAIN_DOES_NOT_KNOW_THE_WEB =
      noClasses()
          .that()
          .resideInAPackage("..domain..")
          .should()
          .dependOnClassesThat()
          .resideInAnyPackage("..messaging..", "org.springframework.web..")
          .because("the domain has no transport types");
}
