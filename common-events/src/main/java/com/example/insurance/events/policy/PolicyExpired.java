package com.example.insurance.events.policy;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Payload of {@code PolicyExpired} on {@code policy.events} (PLAN 10, M8b). Sent once, by the
 * expiry job, the day after an active policy's last day of cover. Incidents up to and including
 * {@code expiredOn} stay covered.
 *
 * @param policyId the policy
 * @param policyNumber human-readable policy number
 * @param holderUserId the customer who holds the policy, so a consumer can notify them
 * @param policyVersion increases with every change to this policy
 * @param expiredOn the last day of cover (the policy's end date)
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record PolicyExpired(
    UUID policyId,
    String policyNumber,
    UUID holderUserId,
    long policyVersion,
    LocalDate expiredOn) {

  /** Payload schema version for the envelope's {@code version} field. */
  public static final int VERSION = 1;
}
