package com.example.insurance.events.policy;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Payload of {@code PolicyCancelled} on {@code policy.events} (PLAN 10, M4b). In M4b the only
 * producer is the unpaid-policy job, which sends {@code reason = UNPAID}. The cancellation rule for
 * paid policies (D7) arrives in M8.
 *
 * @param policyId the policy
 * @param policyNumber human-readable policy number
 * @param holderUserId the customer who holds the policy, so a consumer can notify them
 * @param policyVersion increases with every change to this policy
 * @param reason a stable code, for example {@code UNPAID}
 * @param effectiveDate the day cover ends because of the cancellation
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record PolicyCancelled(
    UUID policyId,
    String policyNumber,
    UUID holderUserId,
    long policyVersion,
    String reason,
    LocalDate effectiveDate) {

  /** Payload schema version for the envelope's {@code version} field. */
  public static final int VERSION = 1;
}
