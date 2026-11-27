package com.example.insurance.notificationservice.messaging;

/** Kafka topics this service reads (PLAN 10, docs/events.md). */
public final class Topics {

  public static final String USER_EVENTS = "user.events";
  public static final String POLICY_EVENTS = "policy.events";
  public static final String PAYMENT_EVENTS = "payment.events";
  public static final String CLAIM_EVENTS = "claim.events";

  private Topics() {
    // Constants only; not instantiable.
  }
}
