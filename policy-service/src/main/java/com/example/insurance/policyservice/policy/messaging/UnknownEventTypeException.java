package com.example.insurance.policyservice.policy.messaging;

/**
 * A message whose {@code type} this service does not know. Retrying cannot fix it, so the error
 * handler sends it straight to the dead-letter topic (ADR-005, ADR-013).
 */
public class UnknownEventTypeException extends RuntimeException {

  private static final long serialVersionUID = 1L;

  public UnknownEventTypeException(String type) {
    super("Unknown event type: " + type);
  }
}
