package com.example.insurance.events.payment;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.math.BigDecimal;
import java.util.UUID;

/**
 * Payload of {@code PremiumPaid} on {@code payment.events} (PLAN 10, M4b). The envelope's aggregate
 * id is the policy id, so policy-service can route it without a lookup.
 *
 * @param paymentId the payment that succeeded
 * @param policyId the policy the premium is for (the reference of the payment)
 * @param policyNumber human-readable policy number, for notifications and logs
 * @param holderUserId the customer who paid, so a consumer can notify them
 * @param amount the amount paid, as the server quoted it
 * @param currency ISO currency code, KES in v1
 * @param providerRef the mock provider's reference for the charge
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record PremiumPaid(
    UUID paymentId,
    UUID policyId,
    String policyNumber,
    UUID holderUserId,
    BigDecimal amount,
    String currency,
    String providerRef) {

  /** Payload schema version for the envelope's {@code version} field. */
  public static final int VERSION = 1;
}
