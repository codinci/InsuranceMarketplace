package com.example.insurance.policyservice.catalogue.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * The terms of a published plan (PLAN 13). Rating tables are left out: premiums come from quotes
 * (M4), so the client never computes a price itself.
 */
@Schema(description = "The terms of a published plan.")
public record PlanDetailResponse(
    UUID id,
    @Schema(example = "ACACIA") String insurerCode,
    @Schema(example = "Acacia Assurance (fictional)") String insurerName,
    @Schema(example = "MEDICAL") String productCode,
    @Schema(example = "STANDARD") String code,
    @Schema(example = "Acacia Standard") String name,
    @Schema(example = "1") int version,
    @Schema(example = "KES") String currency,
    LocalDate saleFrom,
    LocalDate saleTo,
    @Schema(description = "AT_START or NEXT_BIRTHDAY", example = "NEXT_BIRTHDAY") String ageBasis,
    EligibilityView eligibility,
    List<ModuleView> modules,
    List<WaitingRuleView> waitingRules,
    List<String> exclusions,
    @Schema(description = "Days after an incident within which a claim must be filed.")
        int filingDeadlineDays,
    CancellationView cancellation) {

  public PlanDetailResponse {
    modules = List.copyOf(modules);
    waitingRules = List.copyOf(waitingRules);
    exclusions = List.copyOf(exclusions);
  }

  @Schema(description = "Who the plan accepts. Ages are in the plan's age basis.")
  public record EligibilityView(
      List<String> coverTypes,
      AgeRangeView principalAge,
      @Schema(description = "Same as principalAge when the plan sets none.") AgeRangeView spouseAge,
      @Schema(description = "Null when the plan has no children.") ChildAgeView childAge,
      int maxChildren) {

    public EligibilityView {
      coverTypes = List.copyOf(coverTypes);
    }
  }

  @Schema(description = "An inclusive age range.")
  public record AgeRangeView(int min, int max) {}

  @Schema(description = "Child ages, with an optional higher limit for students.")
  public record ChildAgeView(
      int min, int max, Integer maxIfStudent, Boolean studentEvidenceRequired) {}

  @Schema(description = "A module and its options.")
  public record ModuleView(String code, boolean mandatory, List<OptionView> options) {

    public ModuleView {
      options = List.copyOf(options);
    }
  }

  @Schema(description = "An option of a module and its benefit tree.")
  public record OptionView(@Schema(example = "OP-40K") String code, BenefitView benefits) {}

  @Schema(description = "A benefit node (docs/catalogue-schema.md).")
  public record BenefitView(
      @Schema(example = "CONSULTATION") String code,
      @Schema(example = "REIMBURSEMENT") String kind,
      @Schema(description = "Null when only the parent limits it.") LimitView limit,
      CopayView copay,
      List<String> requiredDocuments,
      boolean preAuthRequired,
      List<BenefitView> children) {

    public BenefitView {
      requiredDocuments = List.copyOf(requiredDocuments);
      children = List.copyOf(children);
    }
  }

  @Schema(description = "A limit. Amounts in the plan currency.")
  public record LimitView(
      BigDecimal amount,
      @Schema(example = "PER_YEAR") String period,
      @Schema(example = "PER_MEMBER") String scope,
      BigDecimal maxPerVisit,
      Integer maxCount) {}

  @Schema(description = "A co-pay. A PERCENT value is a fraction: 0.1 is 10%.")
  public record CopayView(@Schema(example = "PERCENT") String type, BigDecimal value) {}

  @Schema(description = "A waiting period and when it applies (docs/catalogue-schema.md, D31).")
  public record WaitingRuleView(
      String code,
      int days,
      int precedence,
      String cause,
      Boolean surgical,
      List<String> conditionTags,
      List<String> benefits) {

    public WaitingRuleView {
      conditionTags = List.copyOf(conditionTags);
      benefits = List.copyOf(benefits);
    }
  }

  @Schema(description = "The cancellation rule.")
  public record CancellationView(
      int noticeDays,
      @Schema(example = "PRO_RATA") String refundModel,
      Integer noRefundAfterMonths,
      Boolean noRefundIfClaims) {}
}
