package com.example.insurance.policyservice.policy.dto;

import com.example.insurance.events.policy.TermsSnapshot;
import com.example.insurance.policyservice.catalogue.api.CoverType;
import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Schema(description = "A policy with its members and the terms it was sold with.")
public record PolicyResponse(
    UUID id,
    @Schema(example = "POL-ACACIA-2026-000123") String policyNumber,
    @Schema(example = "PENDING_PAYMENT") String status,
    @Schema(
            description =
                "The premium payment. Pay it with POST /api/v1/payments/{paymentId}/pay (M4b).")
        UUID paymentId,
    String insurerCode,
    UUID planId,
    String planCode,
    int planVersion,
    CoverType coverType,
    LocalDate startDate,
    LocalDate endDate,
    PremiumView premium,
    List<PolicyMemberView> members,
    @Schema(description = "Frozen at purchase (ADR-011).") TermsSnapshot termsSnapshot,
    @Schema(
            description =
                "Set once cancelled: the first day without cover. Incidents before it stay"
                    + " covered (M8b).")
        LocalDate cancelEffectiveDate,
    @Schema(description = "Set once cancelled: UNPAID or CUSTOMER.", example = "CUSTOMER")
        String cancelReason,
    @Schema(
            description =
                "Set by a customer cancellation: the refund the plan's rule gives, paid back to"
                    + " the holder by payment-service (ADR-021).",
            example = "33500.00")
        BigDecimal refundAmount) {

  public PolicyResponse {
    members = List.copyOf(members);
  }
}
