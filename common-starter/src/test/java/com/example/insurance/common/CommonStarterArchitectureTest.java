package com.example.insurance.common;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

@AnalyzeClasses(
    packages = "com.example.insurance",
    importOptions = ImportOption.DoNotIncludeTests.class)
class CommonStarterArchitectureTest {

  @ArchTest
  static final ArchRule COMMON_STARTER_HAS_NO_DOMAIN_CODE =
      noClasses()
          .that()
          .resideInAPackage("com.example.insurance.common..")
          .should()
          .dependOnClassesThat()
          .resideInAPackage("..domain..");
}
