package com.example.insurance.paymentservice.dto;

import com.example.insurance.paymentservice.domain.Payment;
import com.example.insurance.paymentservice.domain.PaymentStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Schema(description = "A premium payment and where it stands.")
public record PaymentResponse(
    @Schema(description = "Payment id, used in POST /api/v1/payments/{id}/pay") UUID id,
    @Schema(description = "PENDING, SUCCEEDED, FAILED or EXPIRED", example = "PENDING")
        String status,
    @Schema(description = "POLICY (a premium), CLAIM (a payout) or REFUND", example = "POLICY")
        String referenceType,
    UUID referenceId,
    @Schema(example = "POL-ACACIA-2026-000001") String referenceNumber,
    @Schema(example = "12500.00") BigDecimal amount,
    @Schema(example = "KES") String currency,
    @Schema(description = "The mock provider's reference, once a payment succeeds")
        String providerRef,
    Instant createdAt) {

  /**
   * The payment as the caller should see it. A replayed pay request answers with the status its
   * original attempt produced, so the status is passed in.
   */
  public static PaymentResponse of(Payment payment, PaymentStatus status) {
    return new PaymentResponse(
        payment.getId(),
        status.name(),
        payment.getReferenceType(),
        payment.getReferenceId(),
        payment.getReferenceNumber(),
        payment.getAmount(),
        payment.getCurrency(),
        payment.getProviderRef(),
        payment.getCreatedAt());
  }
}
