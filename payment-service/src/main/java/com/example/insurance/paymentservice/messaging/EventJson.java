package com.example.insurance.paymentservice.messaging;

import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.json.JsonMapper;

/**
 * The one JSON mapper for event envelopes in this service. Built here rather than injected, like
 * policy-service's {@code PolicyJson}, so amounts always read as {@code BigDecimal}.
 */
final class EventJson {

  static final JsonMapper MAPPER =
      JsonMapper.builder().enable(DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS).build();

  private EventJson() {
    // Holder only; not instantiable.
  }
}
