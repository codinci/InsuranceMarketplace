package com.example.insurance.events.payment;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Payload of {@code RefundSucceeded} on {@code payment.events} (PLAN 10, M8b). The envelope's
 * aggregate id is the policy id, the same key as the premium events of that policy.
 *
 * @param paymentId the refund payment that succeeded
 * @param policyId the cancelled policy (the reference of the refund)
 * @param policyNumber human-readable policy number, for notifications and logs
 * @param holderUserId the policyholder who was refunded
 * @param amount the amount refunded, as policy-service computed it
 * @param currency ISO currency code, KES in v1
 * @param providerRef the mock provider's reference for the refund
 * @param refundedAt when the provider confirmed the refund
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record RefundSucceeded(
    UUID paymentId,
    UUID policyId,
    String policyNumber,
    UUID holderUserId,
    BigDecimal amount,
    String currency,
    String providerRef,
    Instant refundedAt) {

  /** Payload schema version for the envelope's {@code version} field. */
  public static final int VERSION = 1;
}
