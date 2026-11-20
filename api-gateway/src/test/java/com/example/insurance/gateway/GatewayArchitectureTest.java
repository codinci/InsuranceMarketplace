package com.example.insurance.gateway;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noFields;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;
import org.springframework.beans.factory.annotation.Autowired;

/** Constructor injection only, as in every service (CLAUDE.md Domain rules). */
@AnalyzeClasses(
    packages = "com.example.insurance.gateway",
    importOptions = ImportOption.DoNotIncludeTests.class)
class GatewayArchitectureTest {

  @ArchTest
  static final ArchRule NO_FIELD_INJECTION =
      noFields()
          .should()
          .beAnnotatedWith(Autowired.class)
          .because("constructor injection only (CLAUDE.md Domain rules)");
}
