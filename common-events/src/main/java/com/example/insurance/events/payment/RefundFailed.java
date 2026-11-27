package com.example.insurance.events.payment;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Payload of {@code RefundFailed} on {@code payment.events} (PLAN 10, M8b). Sent for the first
 * attempt and again for every failed retry. The envelope's aggregate id is the policy id.
 *
 * @param paymentId the refund payment that failed
 * @param policyId the cancelled policy (the reference of the refund)
 * @param policyNumber human-readable policy number, for notifications and logs
 * @param holderUserId the policyholder who was to be refunded
 * @param amount the amount that could not be refunded
 * @param currency ISO currency code, KES in v1
 * @param reason stable failure code from the provider, never free text
 * @param failedAt when the provider reported the failure
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record RefundFailed(
    UUID paymentId,
    UUID policyId,
    String policyNumber,
    UUID holderUserId,
    BigDecimal amount,
    String currency,
    String reason,
    Instant failedAt) {

  /** Payload schema version for the envelope's {@code version} field. */
  public static final int VERSION = 1;
}
