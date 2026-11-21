package com.example.insurance.claimservice.messaging;

/** Topic names (docs/events.md). Claim-service reads {@code policy.events} in M5a. */
public final class Topics {

  public static final String POLICY_EVENTS = "policy.events";

  private Topics() {
    // Constants only; not instantiable.
  }
}
