package com.example.insurance.common.web;

import java.util.Optional;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Correlation id rules shared by the servlet filter (services) and the reactive filter
 * (api-gateway), so both stacks accept and generate exactly the same ids (PLAN 10, 15). Plain Java:
 * no web dependency.
 */
public final class CorrelationIds {

  public static final String HEADER = "X-Correlation-Id";
  public static final String MDC_KEY = "correlationId";

  // Short plain tokens only. A value with a newline could otherwise write a fake line into the
  // logs.
  private static final Pattern VALID_ID = Pattern.compile("[A-Za-z0-9._-]{1,64}");

  private CorrelationIds() {
    // Constants and rules only; not instantiable.
  }

  /** Returns the incoming id when it is valid, otherwise a new UUID. */
  public static String resolve(String incoming) {
    return Optional.ofNullable(incoming)
        .filter(value -> VALID_ID.matcher(value).matches())
        .orElseGet(() -> UUID.randomUUID().toString());
  }
}
