package com.example.insurance.messaging;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

/** common-messaging is plumbing only: it never knows a service (CLAUDE.md layout). */
@AnalyzeClasses(
    packages = "com.example.insurance.messaging",
    importOptions = ImportOption.DoNotIncludeTests.class)
class CommonMessagingArchitectureTest {

  @ArchTest
  static final ArchRule NO_SERVICE_CODE =
      noClasses()
          .should()
          .dependOnClassesThat()
          .resideInAnyPackage(
              "com.example.insurance.auth..",
              "com.example.insurance.policyservice..",
              "com.example.insurance.paymentservice..",
              "com.example.insurance.claimservice..",
              "com.example.insurance.gateway..")
          .because("shared plumbing must not depend on any service");

  @ArchTest
  static final ArchRule NO_PAYLOAD_TYPES =
      noClasses()
          .should()
          .dependOnClassesThat()
          .resideInAnyPackage(
              "com.example.insurance.events.policy..", "com.example.insurance.events.payment..")
          .because("the module carries envelopes; each service knows its own payloads");
}
