package com.example.insurance.events.claim;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.time.Instant;
import java.util.UUID;

/**
 * Payload of {@code ClaimInfoRequested} on {@code claim.events} (PLAN 10, M6). Sent when a reviewer
 * asks the customer for more information. The customer answers with {@code POST
 * /claims/{id}/respond}, which returns the claim to review.
 *
 * @param claimId the claim
 * @param claimNumber the human-readable claim number
 * @param policyId the policy the claim is against
 * @param holderUserId the customer who is asked
 * @param message what the reviewer needs; shown to the customer
 * @param requestedAt when the information was requested
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record ClaimInfoRequested(
    UUID claimId,
    String claimNumber,
    UUID policyId,
    UUID holderUserId,
    String message,
    Instant requestedAt) {

  /** Payload schema version for the envelope's {@code version} field. */
  public static final int VERSION = 1;
}
