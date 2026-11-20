package com.example.insurance.policyservice.catalogue.rating;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.insurance.policyservice.catalogue.CatalogueFixtures;
import com.example.insurance.policyservice.catalogue.domain.Relationship;
import com.example.insurance.policyservice.catalogue.domain.Sex;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import tools.jackson.databind.JsonNode;

/**
 * Prices every case in {@code catalogue/golden-premiums.json} and compares each line with the
 * expected amounts, which were worked out by hand (PLAN 18, golden data).
 */
class GoldenPremiumTest {

  private final PremiumCalculator calculator = new PremiumCalculator(CatalogueFixtures.registry());

  static Stream<Arguments> cases() {
    JsonNode cases =
        CatalogueFixtures.MAPPER.readTree(CatalogueFixtures.resource("golden-premiums.json"));
    return cases.valueStream().map(c -> Arguments.of(c.get("name").asString(), c));
  }

  @ParameterizedTest(name = "{0}")
  @MethodSource("cases")
  void matchesTheExpectedBreakdown(String name, JsonNode golden) {
    List<RatedMember> members =
        golden
            .get("members")
            .valueStream()
            .map(m ->
                new RatedMember(
                    Relationship.valueOf(m.get("relationship").asString()),
                    Sex.valueOf(m.get("sex").asString()),
                    LocalDate.parse(m.get("dateOfBirth").asString())))
            .toList();
    Map<String, String> selection = new LinkedHashMap<>();
    golden
        .get("selection")
        .properties()
        .forEach(e -> selection.put(e.getKey(), e.getValue().asString()));

    PremiumBreakdown breakdown =
        calculator.price(
            CatalogueFixtures.validPlan(golden.get("plan").asString()),
            selection,
            members,
            LocalDate.parse(golden.get("startDate").asString()));

    JsonNode expected = golden.get("expected");
    assertThat(
            asMap(
                breakdown.modules().stream().map(m -> Map.entry(m.module(), m.amount())).toList()))
        .isEqualTo(expectedMap(expected.get("modules")));
    assertThat(breakdown.subtotal()).isEqualTo(new BigDecimal(expected.get("subtotal").asString()));
    assertThat(
            asMap(
                breakdown.adjustments().stream()
                    .map(a -> Map.entry(a.code(), a.amount()))
                    .toList()))
        .isEqualTo(expectedMap(expected.get("adjustments")));
    assertThat(breakdown.adjustedSubtotal())
        .isEqualTo(new BigDecimal(expected.get("adjustedSubtotal").asString()));
    assertThat(
            asMap(breakdown.levies().stream().map(l -> Map.entry(l.code(), l.amount())).toList()))
        .isEqualTo(expectedMap(expected.get("levies")));
    assertThat(breakdown.annualPremium())
        .isEqualTo(new BigDecimal(expected.get("annualPremium").asString()));
  }

  private static Map<String, BigDecimal> asMap(List<Map.Entry<String, BigDecimal>> entries) {
    Map<String, BigDecimal> map = new LinkedHashMap<>();
    entries.forEach(e -> map.put(e.getKey(), e.getValue()));
    return map;
  }

  private static Map<String, BigDecimal> expectedMap(JsonNode node) {
    Map<String, BigDecimal> map = new LinkedHashMap<>();
    node.properties().forEach(e -> map.put(e.getKey(), new BigDecimal(e.getValue().asString())));
    return map;
  }
}
