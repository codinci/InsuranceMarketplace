package com.example.insurance.policyservice.catalogue.domain;

import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.json.JsonMapper;

/** The JSON settings every catalogue reader shares. */
public final class PlanJson {

  private PlanJson() {}

  /**
   * Decimal numbers become {@code BigDecimal}, never {@code double}, so money and fractions such as
   * 0.005 stay exact. Unknown fields fail, so the records and the schema cannot drift apart.
   */
  public static JsonMapper newMapper() {
    return JsonMapper.builder()
        .enable(DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS)
        .enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
        .build();
  }
}
