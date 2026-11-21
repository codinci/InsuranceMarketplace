package com.example.insurance.policyservice.policy.messaging;

/** Kafka topics this service reads or writes (PLAN 10, docs/events.md). */
public final class Topics {

  public static final String POLICY_EVENTS = "policy.events";
  public static final String PAYMENT_EVENTS = "payment.events";

  /** Name recorded in the inbox, so a future second consumer does not share processed ids. */
  public static final String CONSUMER_NAME = "policy-service";

  private Topics() {
    // Constants only; not instantiable.
  }
}
