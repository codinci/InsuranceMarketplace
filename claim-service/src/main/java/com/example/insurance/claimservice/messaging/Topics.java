package com.example.insurance.claimservice.messaging;

/** Topic names (docs/events.md). Claim-service reads {@code policy.events} in M5a. */
public final class Topics {

  public static final String POLICY_EVENTS = "policy.events";
  public static final String CLAIM_EVENTS = "claim.events";

  private Topics() {
    // Constants only; not instantiable.
  }
}
