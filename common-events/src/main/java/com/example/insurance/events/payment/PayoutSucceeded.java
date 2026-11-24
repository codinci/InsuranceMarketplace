package com.example.insurance.events.payment;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Payload of {@code PayoutSucceeded} on {@code payment.events} (PLAN 10, M7). The envelope's
 * aggregate id is the claim id, so claim-service can route it without a lookup and all events of
 * one claim stay in order.
 *
 * @param paymentId the payout payment that succeeded
 * @param claimId the claim that was paid (the reference of the payment)
 * @param claimNumber human-readable claim number, for notifications and logs
 * @param holderUserId the policyholder who was paid
 * @param amount the amount paid, as claim-service approved it
 * @param currency ISO currency code, KES in v1
 * @param providerRef the mock provider's reference for the payout
 * @param paidAt when the provider confirmed the payout
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record PayoutSucceeded(
    UUID paymentId,
    UUID claimId,
    String claimNumber,
    UUID holderUserId,
    BigDecimal amount,
    String currency,
    String providerRef,
    Instant paidAt) {

  /** Payload schema version for the envelope's {@code version} field. */
  public static final int VERSION = 1;
}
