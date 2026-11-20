package com.example.insurance.policyservice.policy.dto;

import com.example.insurance.policyservice.catalogue.api.CoverType;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Schema(description = "A server-priced quote.")
public record QuoteResponse(
    UUID id,
    UUID planId,
    @Schema(example = "ACACIA") String insurerCode,
    @Schema(example = "STANDARD") String planCode,
    int planVersion,
    CoverType coverType,
    LocalDate startDate,
    List<QuoteMemberRequest> members,
    Map<String, String> selectedModules,
    PremiumView premium,
    Instant validUntil,
    @Schema(description = "OPEN, USED or EXPIRED.", example = "OPEN") String status) {

  public QuoteResponse {
    members = List.copyOf(members);
    selectedModules = Map.copyOf(selectedModules);
  }
}
