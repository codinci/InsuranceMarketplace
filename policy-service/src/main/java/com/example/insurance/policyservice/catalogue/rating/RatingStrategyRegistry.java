package com.example.insurance.policyservice.catalogue.rating;

import com.example.insurance.policyservice.catalogue.domain.PlanDefinition;
import com.example.insurance.policyservice.catalogue.domain.PlanJson;
import com.example.insurance.policyservice.catalogue.domain.Violation;
import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Finds the strategy for a {@code rating.model}. Spring passes in every {@link RatingStrategy}
 * bean, so registering a new strategy needs no change here (ADR-003).
 */
@Component
public final class RatingStrategyRegistry {

  private final Map<String, RatingStrategy<?>> byModel = new HashMap<>();
  private final JsonMapper mapper = PlanJson.newMapper();

  public RatingStrategyRegistry(List<RatingStrategy<?>> strategies) {
    for (RatingStrategy<?> strategy : strategies) {
      RatingStrategy<?> previous = byModel.putIfAbsent(strategy.model(), strategy);
      if (previous != null) {
        throw new IllegalStateException(
            "Two rating strategies handle the model " + strategy.model() + ".");
      }
    }
  }

  public Optional<RatingStrategy<?>> find(String model) {
    return Optional.ofNullable(model).map(byModel::get);
  }

  /** Semantic checks of one option's {@code rating}; an unknown model is a violation. */
  public List<Violation> validate(JsonNode rating, PlanDefinition plan, String path) {
    String model = modelOf(rating);
    Optional<RatingStrategy<?>> strategy = find(model);
    if (strategy.isEmpty()) {
      return List.of(
          new Violation(
              RatingException.UNKNOWN_RATING_MODEL,
              path + "/model",
              "No rating strategy handles the model " + model + "."));
    }
    try {
      return validateWith(strategy.get(), rating, plan, path);
    } catch (JacksonException e) {
      return List.of(
          new Violation(
              RatingException.RATING_CONFIG_INVALID,
              path,
              "The rating configuration cannot be read for the model " + model + "."));
    }
  }

  /** The unrounded annual premium of one option. */
  public BigDecimal price(JsonNode rating, RatingContext context) {
    String model = modelOf(rating);
    RatingStrategy<?> strategy =
        find(model)
            .orElseThrow(() ->
                new RatingException(
                    RatingException.UNKNOWN_RATING_MODEL,
                    "No rating strategy handles the model " + model + "."));
    try {
      return priceWith(strategy, rating, context);
    } catch (JacksonException e) {
      throw new RatingException(
          RatingException.RATING_CONFIG_INVALID,
          "The rating configuration cannot be read for the model " + model + ".");
    }
  }

  private <C> List<Violation> validateWith(
      RatingStrategy<C> strategy, JsonNode rating, PlanDefinition plan, String path) {
    return strategy.validate(mapper.treeToValue(rating, strategy.configType()), plan, path);
  }

  private <C> BigDecimal priceWith(
      RatingStrategy<C> strategy, JsonNode rating, RatingContext context) {
    return strategy.price(mapper.treeToValue(rating, strategy.configType()), context);
  }

  private static String modelOf(JsonNode rating) {
    if (rating == null || !rating.hasNonNull("model")) {
      return null;
    }
    return rating.get("model").asString();
  }
}
