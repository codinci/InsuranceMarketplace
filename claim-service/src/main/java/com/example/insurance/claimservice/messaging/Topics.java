package com.example.insurance.claimservice.messaging;

/**
 * Topic names (docs/events.md). Claim-service reads {@code policy.events} (M5a) and {@code
 * payment.events} (M7).
 */
public final class Topics {

  public static final String POLICY_EVENTS = "policy.events";
  public static final String PAYMENT_EVENTS = "payment.events";
  public static final String CLAIM_EVENTS = "claim.events";

  private Topics() {
    // Constants only; not instantiable.
  }
}
