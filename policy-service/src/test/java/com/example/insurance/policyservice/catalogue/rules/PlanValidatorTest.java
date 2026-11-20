package com.example.insurance.policyservice.catalogue.rules;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import com.example.insurance.policyservice.catalogue.CatalogueFixtures;
import com.example.insurance.policyservice.catalogue.domain.Violation;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import tools.jackson.databind.JsonNode;

class PlanValidatorTest {

  private final PlanValidator validator = CatalogueFixtures.validator();

  @ParameterizedTest
  @ValueSource(strings = {"acacia-standard.v1.json", "baobab-family.v1.json"})
  void theValidFixturesPassBothLayers(String file) {
    ValidationReport report = validator.validate(CatalogueFixtures.resource("valid/" + file));
    assertThat(report.violations()).isEmpty();
    assertThat(report.valid()).isTrue();
    assertThat(report.validPlan()).isPresent();
  }

  @Test
  void textThatIsNotJsonIsRefused() {
    ValidationReport report = validator.validate("{ not json");
    assertThat(report.violations())
        .extracting(Violation::code, Violation::path)
        .containsExactly(tuple(PlanValidator.MALFORMED_JSON, ""));
    assertThat(report.validPlan()).isEmpty();
  }

  @Test
  void everyViolationIsReportedNotOnlyTheFirst() {
    JsonNode plan = CatalogueFixtures.validPlanJson("acacia-standard.v1.json");
    CatalogueFixtures.set(plan, "/plan/currency", CatalogueFixtures.MAPPER.readTree("\"USD\""));
    CatalogueFixtures.set(
        plan, "/plan/modules/0/mandatory", CatalogueFixtures.MAPPER.readTree("false"));
    assertThat(validator.validate(plan).violations())
        .extracting(Violation::code)
        .contains("CURRENCY_NOT_SUPPORTED", "NO_MANDATORY_MODULE");
  }

  static Stream<Arguments> invalidCases() {
    JsonNode cases =
        CatalogueFixtures.MAPPER.readTree(CatalogueFixtures.resource("invalid-cases.json"));
    return cases.valueStream().map(c -> Arguments.of(c.get("name").asString(), c));
  }

  @ParameterizedTest(name = "{0}")
  @MethodSource("invalidCases")
  void eachInvalidCaseGivesItsCodeAndPath(String name, JsonNode invalidCase) {
    JsonNode plan = CatalogueFixtures.validPlanJson(invalidCase.get("base").asString());
    for (JsonNode mutation : invalidCase.get("mutations")) {
      String path = mutation.get("path").asString();
      if ("remove".equals(mutation.get("op").asString())) {
        CatalogueFixtures.remove(plan, path);
      } else {
        CatalogueFixtures.set(plan, path, mutation.get("value"));
      }
    }
    String code = invalidCase.get("code").asString();

    ValidationReport report = validator.validate(plan);

    assertThat(report.valid()).isFalse();
    assertThat(report.validPlan()).isEmpty();
    assertThat(report.violations())
        .extracting(Violation::code, Violation::path)
        .contains(tuple(code, invalidCase.get("path").asString()));
    if (!PlanSchemaValidator.SCHEMA_VIOLATION.equals(code)) {
      assertThat(report.violations())
          .extracting(Violation::code)
          .doesNotContain(PlanSchemaValidator.SCHEMA_VIOLATION);
    }
  }
}
