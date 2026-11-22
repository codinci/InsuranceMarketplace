package com.example.insurance.events.payment;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Payload of {@code PayoutFailed} on {@code payment.events} (PLAN 10, M7). Sent for the first
 * attempt and again for every failed retry. The envelope's aggregate id is the claim id.
 *
 * @param paymentId the payout payment that failed
 * @param claimId the claim that could not be paid (the reference of the payment)
 * @param claimNumber human-readable claim number, for notifications and logs
 * @param holderUserId the policyholder who was to be paid
 * @param amount the amount that could not be paid
 * @param currency ISO currency code, KES in v1
 * @param reason stable failure code from the provider, never free text
 * @param failedAt when the provider reported the failure
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record PayoutFailed(
    UUID paymentId,
    UUID claimId,
    String claimNumber,
    UUID holderUserId,
    BigDecimal amount,
    String currency,
    String reason,
    Instant failedAt) {

  /** Payload schema version for the envelope's {@code version} field. */
  public static final int VERSION = 1;
}
