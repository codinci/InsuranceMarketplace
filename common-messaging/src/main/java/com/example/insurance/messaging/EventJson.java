package com.example.insurance.messaging;

import com.example.insurance.events.EventEnvelope;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.JavaType;
import tools.jackson.databind.json.JsonMapper;

/**
 * Reads and writes event envelopes and stored payloads with one mapper for every service. Amounts
 * always read as {@code BigDecimal}, never as {@code double} (CLAUDE.md money rule).
 */
public final class EventJson {

  private static final JsonMapper MAPPER =
      JsonMapper.builder().enable(DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS).build();

  private EventJson() {
    // Static helpers only; not instantiable.
  }

  /** The envelope as JSON, exactly as it is sent to Kafka. */
  public static String write(Object envelope) {
    return MAPPER.writeValueAsString(envelope);
  }

  /** Any value as JSON, for a payload stored in a jsonb column. */
  public static String writeValue(Object value) {
    return MAPPER.writeValueAsString(value);
  }

  /** A value stored as JSON (the inverse of {@link #writeValue}). */
  public static <T> T read(String json, Class<T> type) {
    return MAPPER.readValue(json, type);
  }

  /**
   * The envelope's {@code payload} exactly as received, unknown fields included. A consumer that
   * stores the payload keeps it whole this way, instead of what its own record happens to model.
   * Decimals keep their scale.
   */
  public static String payloadJson(String message) {
    return MAPPER.writeValueAsString(MAPPER.readTree(message).get("payload"));
  }

  /** The {@code type} of a message, read before choosing the payload class. */
  public static String typeOf(String message) {
    return MAPPER.readValue(message, Header.class).type();
  }

  /** The whole envelope with its payload read as {@code payloadType}. */
  public static <T> EventEnvelope<T> envelope(String message, Class<T> payloadType) {
    JavaType type =
        MAPPER.getTypeFactory().constructParametricType(EventEnvelope.class, payloadType);
    return MAPPER.readValue(message, type);
  }

  /** Only the type, so the right payload class can be chosen before reading the rest. */
  @JsonIgnoreProperties(ignoreUnknown = true)
  record Header(String type) {}
}
