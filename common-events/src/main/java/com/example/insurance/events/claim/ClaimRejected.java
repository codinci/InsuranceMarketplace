package com.example.insurance.events.claim;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.time.Instant;
import java.util.UUID;

/**
 * Payload of {@code ClaimRejected} on {@code claim.events} (PLAN 10, M6). Sent when a reviewer
 * rejects a claim during review. A claim rejected by the automatic checks at submission is not
 * announced (ADR-016).
 *
 * @param claimId the claim
 * @param claimNumber the human-readable claim number
 * @param policyId the policy the claim is against
 * @param holderUserId the customer who filed the claim
 * @param reason the reviewer's reason, shown to the customer
 * @param rejectedAt when the claim was rejected
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record ClaimRejected(
    UUID claimId,
    String claimNumber,
    UUID policyId,
    UUID holderUserId,
    String reason,
    Instant rejectedAt) {

  /** Payload schema version for the envelope's {@code version} field. */
  public static final int VERSION = 1;
}
