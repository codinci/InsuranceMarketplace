package com.example.insurance.policyservice.policy.service;

import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.json.JsonMapper;

/** JSON for the policy package's jsonb columns. Decimals stay {@code BigDecimal}. */
final class PolicyJson {

  static final JsonMapper MAPPER =
      JsonMapper.builder().enable(DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS).build();

  private PolicyJson() {}

  static String write(Object value) {
    return MAPPER.writeValueAsString(value);
  }

  static <T> T read(String json, Class<T> type) {
    return MAPPER.readValue(json, type);
  }

  static <T> T read(String json, TypeReference<T> type) {
    return MAPPER.readValue(json, type);
  }
}
