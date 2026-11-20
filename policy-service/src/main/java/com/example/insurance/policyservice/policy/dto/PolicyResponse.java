package com.example.insurance.policyservice.policy.dto;

import com.example.insurance.events.policy.TermsSnapshot;
import com.example.insurance.policyservice.catalogue.api.CoverType;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Schema(description = "A policy with its members and the terms it was sold with.")
public record PolicyResponse(
    UUID id,
    @Schema(example = "POL-ACACIA-2026-000123") String policyNumber,
    @Schema(example = "PENDING_PAYMENT") String status,
    String insurerCode,
    UUID planId,
    String planCode,
    int planVersion,
    CoverType coverType,
    LocalDate startDate,
    LocalDate endDate,
    PremiumView premium,
    List<PolicyMemberView> members,
    @Schema(description = "Frozen at purchase (ADR-011).") TermsSnapshot termsSnapshot) {

  public PolicyResponse {
    members = List.copyOf(members);
  }
}
