package com.example.insurance.events.claim;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Payload of {@code ClaimSubmitted} on {@code claim.events} (PLAN 10, M5b). Sent when a claim
 * passes the hard checks and is accepted for review. A claim rejected by the hard checks is not
 * announced here; its rejection is visible through the claim itself (ADR-016).
 *
 * @param claimId the claim
 * @param claimNumber the human-readable claim number
 * @param policyId the policy the claim is against
 * @param holderUserId the customer who made the claim, so a consumer can notify them
 * @param memberId the member's number on the policy
 * @param moduleCode the module of the benefit
 * @param benefitCode the benefit claimed
 * @param cause ACCIDENT or ILLNESS
 * @param incidentDate the day of the incident
 * @param amountClaimed the amount claimed, KES unless {@code currency} says otherwise
 * @param currency ISO currency code
 * @param submittedAt when the claim was submitted
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record ClaimSubmitted(
    UUID claimId,
    String claimNumber,
    UUID policyId,
    UUID holderUserId,
    String memberId,
    String moduleCode,
    String benefitCode,
    String cause,
    LocalDate incidentDate,
    BigDecimal amountClaimed,
    String currency,
    Instant submittedAt) {

  /** Payload schema version for the envelope's {@code version} field. */
  public static final int VERSION = 1;
}
