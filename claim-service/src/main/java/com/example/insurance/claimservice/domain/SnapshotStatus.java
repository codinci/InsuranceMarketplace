package com.example.insurance.claimservice.domain;

/**
 * What claim-service knows about a policy from its events (PLAN 7). Only the two states that events
 * can produce; PENDING_PAYMENT policies are not in the read model.
 */
public enum SnapshotStatus {
  ACTIVE,
  CANCELLED,
  /** Cover ran to its end date (M8b). Incidents during cover can still be claimed. */
  EXPIRED
}
