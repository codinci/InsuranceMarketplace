package com.example.insurance.policyservice.catalogue.domain;

import tools.jackson.databind.JsonNode;

/**
 * A priced option of a module, for example a limit tier. {@code rating} stays raw JSON so a new
 * rating strategy never changes this type (ADR-003); the strategy reads its own configuration.
 */
public record ModuleOption(String code, BenefitNode benefits, JsonNode rating) {

  public ModuleOption {
    rating = rating == null ? null : rating.deepCopy();
  }

  @Override
  public JsonNode rating() {
    return rating == null ? null : rating.deepCopy();
  }
}
