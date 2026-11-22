package com.example.insurance.events;

/**
 * Names of the event types on the wire (ADR-005, PLAN 10). A consumer selects the payload class by
 * these names, so they are defined once here and never typed by hand in a service.
 */
public final class EventTypes {

  public static final String PREMIUM_PAID = "PremiumPaid";
  public static final String PREMIUM_FAILED = "PremiumFailed";
  public static final String POLICY_ACTIVATED = "PolicyActivated";
  public static final String POLICY_CANCELLED = "PolicyCancelled";
  public static final String CLAIM_SUBMITTED = "ClaimSubmitted";
  public static final String CLAIM_APPROVED = "ClaimApproved";
  public static final String CLAIM_REJECTED = "ClaimRejected";
  public static final String CLAIM_INFO_REQUESTED = "ClaimInfoRequested";

  private EventTypes() {
    // Constants only; not instantiable.
  }
}
