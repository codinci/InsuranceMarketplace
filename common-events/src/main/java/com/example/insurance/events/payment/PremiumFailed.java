package com.example.insurance.events.payment;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.UUID;

/**
 * Payload of {@code PremiumFailed} on {@code payment.events} (PLAN 10, M4b). The policy stays
 * PENDING_PAYMENT; the customer may pay again.
 *
 * @param paymentId the payment that failed
 * @param policyId the policy the premium is for
 * @param policyNumber human-readable policy number
 * @param reason a stable code, for example {@code PROVIDER_DECLINED}
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record PremiumFailed(UUID paymentId, UUID policyId, String policyNumber, String reason) {

  /** Payload schema version for the envelope's {@code version} field. */
  public static final int VERSION = 1;
}
