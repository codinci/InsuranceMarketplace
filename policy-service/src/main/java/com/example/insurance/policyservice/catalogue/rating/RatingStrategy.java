package com.example.insurance.policyservice.catalogue.rating;

import com.example.insurance.policyservice.catalogue.domain.PlanDefinition;
import com.example.insurance.policyservice.catalogue.domain.Violation;
import java.math.BigDecimal;
import java.util.List;

/**
 * One pricing model (ADR-003). A new model is one class implementing this, registered as a Spring
 * bean, plus a schema entry, golden tests and an ADR (docs/catalogue-schema.md).
 *
 * @param <C> the strategy's configuration, read from the option's {@code rating} JSON
 */
public interface RatingStrategy<C> {

  /** The {@code rating.model} value this strategy handles. */
  String model();

  /** The record the {@code rating} JSON is read into. */
  Class<C> configType();

  /** Semantic checks on the configuration against the plan, with paths under {@code path}. */
  List<Violation> validate(C config, PlanDefinition plan, String path);

  /** The annual premium for the option, before adjustments and levies. Not rounded. */
  BigDecimal price(C config, RatingContext context);
}
