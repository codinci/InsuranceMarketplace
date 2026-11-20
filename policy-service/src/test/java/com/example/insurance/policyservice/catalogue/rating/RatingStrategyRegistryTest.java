package com.example.insurance.policyservice.catalogue.rating;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.insurance.policyservice.catalogue.CatalogueFixtures;
import com.example.insurance.policyservice.catalogue.domain.AgeBasis;
import com.example.insurance.policyservice.catalogue.domain.PlanDefinition;
import com.example.insurance.policyservice.catalogue.domain.Violation;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;

class RatingStrategyRegistryTest {

  private final RatingStrategyRegistry registry = CatalogueFixtures.registry();
  private final PlanDefinition plan = CatalogueFixtures.validPlan("acacia-standard.v1.json");
  private final RatingContext context =
      new RatingContext(List.of(), LocalDate.of(2027, 1, 1), AgeBasis.AT_START);

  @Test
  void twoStrategiesForOneModelFailAtStartup() {
    assertThatThrownBy(() ->
            new RatingStrategyRegistry(
                List.of(new AgeBandPerMemberStrategy(), new AgeBandPerMemberStrategy())))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining(AgeBandPerMemberStrategy.MODEL);
  }

  @Test
  void findsEachRegisteredModel() {
    assertThat(registry.find(AgeBandPerMemberStrategy.MODEL)).isPresent();
    assertThat(registry.find(FamilySizeTierStrategy.MODEL)).isPresent();
    assertThat(registry.find(FlatPerEligibleMemberStrategy.MODEL)).isPresent();
    assertThat(registry.find(null)).isEmpty();
  }

  @Test
  void anUnknownModelIsAViolation() {
    List<Violation> violations = registry.validate(json("{\"model\":\"NEW_MODEL\"}"), plan, "/r");
    assertThat(violations)
        .containsExactly(
            new Violation(
                RatingException.UNKNOWN_RATING_MODEL,
                "/r/model",
                "No rating strategy handles the model NEW_MODEL."));
  }

  @Test
  void aMissingModelIsAViolation() {
    assertThat(registry.validate(json("{}"), plan, "/r"))
        .extracting(Violation::code)
        .containsExactly(RatingException.UNKNOWN_RATING_MODEL);
    assertThat(registry.validate(null, plan, "/r"))
        .extracting(Violation::code)
        .containsExactly(RatingException.UNKNOWN_RATING_MODEL);
  }

  @Test
  void anUnreadableConfigurationIsAViolation() {
    JsonNode rating = json("{\"model\":\"AGE_BAND_PER_MEMBER\",\"ageBands\":\"many\"}");
    assertThat(registry.validate(rating, plan, "/r"))
        .extracting(Violation::code, Violation::path)
        .containsExactly(
            org.assertj.core.groups.Tuple.tuple(RatingException.RATING_CONFIG_INVALID, "/r"));
  }

  @Test
  void pricingAnUnknownModelFails() {
    assertThatThrownBy(() -> registry.price(json("{\"model\":\"NEW_MODEL\"}"), context))
        .isInstanceOfSatisfying(
            RatingException.class,
            e -> assertThat(e.code()).isEqualTo(RatingException.UNKNOWN_RATING_MODEL));
  }

  @Test
  void pricingAnUnreadableConfigurationFails() {
    JsonNode rating = json("{\"model\":\"FAMILY_SIZE_TIER\",\"tables\":7}");
    assertThatThrownBy(() -> registry.price(rating, context))
        .isInstanceOfSatisfying(
            RatingException.class,
            e -> assertThat(e.code()).isEqualTo(RatingException.RATING_CONFIG_INVALID));
  }

  private static JsonNode json(String text) {
    return CatalogueFixtures.MAPPER.readTree(text);
  }
}
