package com.example.insurance.claimservice.dto;

import com.example.insurance.claimservice.domain.AssessmentResult;
import com.example.insurance.claimservice.domain.Claim;
import com.example.insurance.claimservice.domain.ClaimAssessment;
import com.example.insurance.messaging.EventJson;
import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * A claim as the customer or a reviewer sees it. A customer sees the automatic checks that decide a
 * claim and the outcome. A reviewer also sees the soft flags, the evaluator's payable estimate and
 * who is reviewing; those fields are null for a customer (PLAN 9.1: flags are signals for
 * reviewers).
 */
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
    @Schema(description = "What was approved for payment, once a reviewer has approved.")
        BigDecimal amountApproved,
    @Schema(
            description =
                "The reviewer's reason: for a rejection, a partial approval or a request.")
        String decisionReason,
    List<AssessmentView> assessments,
    @Schema(description = "Reviewers only: what the evaluator says can be paid.")
        BigDecimal amountEligible,
    @Schema(description = "Reviewers only: true when a soft flag was raised.") Boolean flagged,
    @Schema(description = "Reviewers only: who last acted on the claim.") UUID reviewerId,
    Instant createdAt,
    Instant submittedAt,
    Instant updatedAt,
    @Schema(description = "Increases with every change.") long version) {

  public ClaimResponse {
    conditionTags = List.copyOf(conditionTags);
    assessments = List.copyOf(assessments);
  }

  /** The customer's view: soft flags and the reviewer's working fields are left out. */
  public static ClaimResponse forCustomer(Claim claim, List<ClaimAssessment> checks) {
    return of(
        claim,
        checks.stream().filter(check -> check.getResult() != AssessmentResult.FLAG).toList(),
        false);
  }

  /** The reviewer's view: everything, including the soft flags. */
  public static ClaimResponse forStaff(Claim claim, List<ClaimAssessment> checks) {
    return of(claim, checks, true);
  }

  private static ClaimResponse of(Claim claim, List<ClaimAssessment> checks, boolean staff) {
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
        claim.getAmountApproved(),
        claim.getDecisionReason(),
        checks.stream().map(AssessmentView::of).toList(),
        staff ? claim.getAmountEligible() : null,
        staff ? claim.isFlagged() : null,
        staff ? claim.getReviewerId() : null,
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
