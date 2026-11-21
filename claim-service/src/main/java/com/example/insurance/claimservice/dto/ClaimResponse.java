package com.example.insurance.claimservice.dto;

import com.example.insurance.claimservice.domain.Claim;
import com.example.insurance.claimservice.domain.ClaimAssessment;
import com.example.insurance.messaging.EventJson;
import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/** A claim as the customer sees it, with the result of each automatic check. */
@Schema(description = "A claim, with its details, status and automatic check results.")
public record ClaimResponse(
    UUID id,
    @Schema(example = "CLM-ACACIA-2026-000001") String claimNumber,
    UUID policyId,
    String memberId,
    String moduleCode,
    String benefitCode,
    String cause,
    boolean surgical,
    List<String> conditionTags,
    LocalDate incidentDate,
    String providerName,
    String description,
    BigDecimal amountClaimed,
    String currency,
    @Schema(example = "DRAFT") String status,
    @Schema(description = "Why the claim was rejected, when it was.") String rejectionReason,
    List<AssessmentView> assessments,
    Instant createdAt,
    Instant submittedAt,
    Instant updatedAt,
    @Schema(description = "Increases with every change; send it back when you need one.")
        long version) {

  public ClaimResponse {
    conditionTags = List.copyOf(conditionTags);
    assessments = List.copyOf(assessments);
  }

  public static ClaimResponse of(Claim claim, List<ClaimAssessment> checks) {
    return new ClaimResponse(
        claim.getId(),
        claim.getClaimNumber(),
        claim.getPolicyId(),
        claim.getMemberId(),
        claim.getModuleCode(),
        claim.getBenefitCode(),
        claim.getCause().name(),
        claim.isSurgical(),
        tagsOf(claim),
        claim.getIncidentDate(),
        claim.getProviderName(),
        claim.getDescription(),
        claim.getAmountClaimed(),
        claim.getCurrency(),
        claim.getStatus().name(),
        claim.getRejectionReason(),
        checks.stream().map(AssessmentView::of).toList(),
        claim.getCreatedAt(),
        claim.getSubmittedAt(),
        claim.getUpdatedAt(),
        claim.getVersion());
  }

  private static List<String> tagsOf(Claim claim) {
    String[] tags = EventJson.read(claim.getConditionTags(), String[].class);
    return tags == null ? List.of() : List.of(tags);
  }

  /** One automatic check. */
  @Schema(description = "One automatic check and its outcome.")
  public record AssessmentView(String ruleCode, String result, String detail) {

    static AssessmentView of(ClaimAssessment check) {
      return new AssessmentView(check.getRuleCode(), check.getResult().name(), check.getDetail());
    }
  }
}
