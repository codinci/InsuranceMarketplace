package com.example.insurance.events.policy;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/**
 * The terms a policy was sold with, frozen at purchase (PLAN 8.2, ADR-011). It travels to
 * claim-service in {@code PolicyActivated} (M4b), which interprets it generically: it never needs
 * catalogue types, so enum values are strings and money is {@code BigDecimal}.
 *
 * <p>Additive changes only (ADR-005). A change that is not additive needs a new {@code
 * snapshotVersion}, so policies already sold keep their shape.
 *
 * @param snapshotVersion the shape version; {@link #CURRENT_VERSION} for new policies
 * @param modules only the module options the customer selected
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record TermsSnapshot(
    int snapshotVersion,
    String insurerCode,
    UUID planId,
    String planCode,
    int planVersion,
    String currency,
    String ageBasis,
    List<ModuleTerms> modules,
    List<WaitingRuleTerms> waitingRules,
    List<String> exclusions,
    int filingDeadlineDays,
    CancellationTerms cancellation) {

  public static final int CURRENT_VERSION = 1;

  public TermsSnapshot {
    modules = modules == null ? List.of() : List.copyOf(modules);
    waitingRules = waitingRules == null ? List.of() : List.copyOf(waitingRules);
    exclusions = exclusions == null ? List.of() : List.copyOf(exclusions);
  }

  /** A selected option of a module and its benefit tree. */
  @JsonIgnoreProperties(ignoreUnknown = true)
  public record ModuleTerms(String module, String option, BenefitTerms benefits) {}

  /** A benefit node (docs/catalogue-schema.md). A null limit means only ancestors limit it. */
  @JsonIgnoreProperties(ignoreUnknown = true)
  public record BenefitTerms(
      String code,
      String kind,
      LimitTerms limit,
      CopayTerms copay,
      List<String> requiredDocuments,
      boolean preAuthRequired,
      List<BenefitTerms> children) {

    public BenefitTerms {
      requiredDocuments = requiredDocuments == null ? List.of() : List.copyOf(requiredDocuments);
      children = children == null ? List.of() : List.copyOf(children);
    }
  }

  /** A limit: period PER_YEAR or PER_CLAIM, scope SHARED or PER_MEMBER (D32). */
  @JsonIgnoreProperties(ignoreUnknown = true)
  public record LimitTerms(
      BigDecimal amount, String period, String scope, BigDecimal maxPerVisit, Integer maxCount) {}

  /** A co-pay: PERCENT (a fraction) or FIXED_PER_VISIT. */
  @JsonIgnoreProperties(ignoreUnknown = true)
  public record CopayTerms(String type, BigDecimal value) {}

  /** A waiting rule, resolved by claim-service with the D31 semantics. */
  @JsonIgnoreProperties(ignoreUnknown = true)
  public record WaitingRuleTerms(
      String code,
      int days,
      int precedence,
      String cause,
      Boolean surgical,
      List<String> conditionTags,
      List<String> benefits) {

    public WaitingRuleTerms {
      conditionTags = conditionTags == null ? List.of() : List.copyOf(conditionTags);
      benefits = benefits == null ? List.of() : List.copyOf(benefits);
    }
  }

  /** The cancellation rule (D7). */
  @JsonIgnoreProperties(ignoreUnknown = true)
  public record CancellationTerms(
      int noticeDays, String refundModel, Integer noRefundAfterMonths, Boolean noRefundIfClaims) {}
}
