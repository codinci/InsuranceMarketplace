package com.example.insurance.events.claim;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Payload of {@code ClaimApproved} on {@code claim.events} (PLAN 10, M6). Sent once a reviewer has
 * approved a claim and the approved amount has been reserved against the policy's limits. The
 * payout saga (M7) pays {@code amountApproved} and uses the envelope's event id as its idempotency
 * key.
 *
 * @param claimId the claim
 * @param claimNumber the human-readable claim number
 * @param policyId the policy the claim is against
 * @param holderUserId the customer who holds the policy and is paid
 * @param memberId the member's number on the policy
 * @param benefitCode the benefit claimed
 * @param amountApproved what will be paid; never more than the evaluated payable amount
 * @param currency ISO currency code
 * @param approvedAt when the claim was approved
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record ClaimApproved(
    UUID claimId,
    String claimNumber,
    UUID policyId,
    UUID holderUserId,
    String memberId,
    String benefitCode,
    BigDecimal amountApproved,
    String currency,
    Instant approvedAt) {

  /** Payload schema version for the envelope's {@code version} field. */
  public static final int VERSION = 1;
}
