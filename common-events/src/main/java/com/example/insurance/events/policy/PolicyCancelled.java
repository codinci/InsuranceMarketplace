package com.example.insurance.events.policy;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Payload of {@code PolicyCancelled} on {@code policy.events} (PLAN 10). Two producers: the
 * unpaid-policy job sends {@code reason = UNPAID} (M4b), and a customer cancelling their own policy
 * sends {@code reason = CUSTOMER} with the refund the plan's rule gives (M8b, D7, ADR-021).
 *
 * @param policyId the policy
 * @param policyNumber human-readable policy number
 * @param holderUserId the customer who holds the policy, so a consumer can notify them
 * @param policyVersion increases with every change to this policy
 * @param reason a stable code, for example {@code UNPAID}
 * @param effectiveDate the day cover ends because of the cancellation
 * @param refundAmount the refund the policy-service computed from the policy's terms; zero when the
 *     rule gives none, and {@code null} for an unpaid cancellation and for events sent before M8b
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record PolicyCancelled(
    UUID policyId,
    String policyNumber,
    UUID holderUserId,
    long policyVersion,
    String reason,
    LocalDate effectiveDate,
    BigDecimal refundAmount) {

  /** Payload schema version for the envelope's {@code version} field. */
  public static final int VERSION = 1;

  /** Reason sent by the unpaid-policy job. */
  public static final String REASON_UNPAID = "UNPAID";

  /** Reason sent when the customer cancels their own policy. */
  public static final String REASON_CUSTOMER = "CUSTOMER";
}
