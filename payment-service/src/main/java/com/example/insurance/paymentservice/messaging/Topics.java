package com.example.insurance.paymentservice.messaging;

/** Kafka topics this service reads or writes (PLAN 10, docs/events.md). */
public final class Topics {

  public static final String PAYMENT_EVENTS = "payment.events";
  public static final String POLICY_EVENTS = "policy.events";

  /** Name recorded in the inbox, so a future second consumer does not share processed ids. */
  public static final String CONSUMER_NAME = "payment-service";

  private Topics() {
    // Constants only; not instantiable.
  }
}
