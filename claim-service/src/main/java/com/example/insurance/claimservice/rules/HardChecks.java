package com.example.insurance.claimservice.rules;

import com.example.insurance.claimservice.domain.AssessmentResult;
import com.example.insurance.claimservice.domain.Cause;
import com.example.insurance.events.policy.TermsSnapshot;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * The automatic checks run at submission (PLAN 9). Any FAIL rejects the claim at once, with the
 * reasons. Pure functions of the facts, so every rule is tested as a table (no database, no clock).
 */
public final class HardChecks {

  public static final String POLICY_IN_FORCE = "POLICY_IN_FORCE";
  public static final String INCIDENT_NOT_FUTURE = "INCIDENT_NOT_FUTURE";
  public static final String MEMBER_ON_POLICY = "MEMBER_ON_POLICY";
  public static final String BENEFIT_COVERED = "BENEFIT_COVERED";
  public static final String WAITING_PERIOD_ELAPSED = "WAITING_PERIOD_ELAPSED";
  public static final String FILING_DEADLINE = "FILING_DEADLINE";
  public static final String REQUIRED_DOCUMENTS = "REQUIRED_DOCUMENTS";
  public static final String AMOUNT_POSITIVE = "AMOUNT_POSITIVE";

  private HardChecks() {
    // Static rules only; not instantiable.
  }

  /** What the claim says. */
  public record ClaimFacts(
      String memberId,
      String moduleCode,
      String benefitCode,
      Cause cause,
      boolean surgical,
      Set<String> conditionTags,
      LocalDate incidentDate,
      BigDecimal amountClaimed,
      Set<String> documentTypes) {

    public ClaimFacts {
      conditionTags = Set.copyOf(conditionTags);
      documentTypes = Set.copyOf(documentTypes);
    }
  }

  /** What the policy says, as sold. {@code cancelEffectiveDate} is null unless cancelled. */
  public record PolicyFacts(
      String status,
      LocalDate coverStart,
      LocalDate coverEnd,
      LocalDate cancelEffectiveDate,
      Set<String> memberNumbers,
      TermsSnapshot terms) {

    public PolicyFacts {
      memberNumbers = Set.copyOf(memberNumbers);
    }
  }

  /** Runs every check. {@code today} is the day of submission (the clock, not the request). */
  public static List<Assessment> evaluate(ClaimFacts claim, PolicyFacts policy, LocalDate today) {
    List<Assessment> results = new ArrayList<>();
    results.add(policyInForce(claim, policy));
    results.add(incidentNotFuture(claim, today));
    results.add(memberOnPolicy(claim, policy));

    Optional<List<TermsSnapshot.BenefitTerms>> path =
        BenefitPath.find(policy.terms(), claim.moduleCode(), claim.benefitCode());
    results.add(benefitCovered(claim, path));
    if (path.isPresent()) {
      Set<String> codes = BenefitPath.codes(path.get());
      results.add(waitingPeriod(claim, policy, codes));
      results.add(requiredDocuments(path.get().get(path.get().size() - 1), claim));
    }
    results.add(filingDeadline(claim, policy, today));
    results.add(amountPositive(claim));
    return results;
  }

  static Assessment policyInForce(ClaimFacts claim, PolicyFacts policy) {
    LocalDate incident = claim.incidentDate();
    // An expired policy still covers what happened during cover (M8b).
    boolean coveredPeriod =
        ("ACTIVE".equals(policy.status()) || "EXPIRED".equals(policy.status()))
            && !incident.isBefore(policy.coverStart())
            && !incident.isAfter(policy.coverEnd());
    boolean cancelledBefore =
        "CANCELLED".equals(policy.status())
            && policy.cancelEffectiveDate() != null
            && !incident.isBefore(policy.coverStart())
            && incident.isBefore(policy.cancelEffectiveDate());
    return coveredPeriod || cancelledBefore
        ? Assessment.pass(POLICY_IN_FORCE, "The policy was in force on the incident date.")
        : Assessment.fail(POLICY_IN_FORCE, "The policy was not in force on the incident date.");
  }

  static Assessment incidentNotFuture(ClaimFacts claim, LocalDate today) {
    return claim.incidentDate().isAfter(today)
        ? Assessment.fail(INCIDENT_NOT_FUTURE, "The incident date is in the future.")
        : Assessment.pass(INCIDENT_NOT_FUTURE, "The incident has already happened.");
  }

  static Assessment memberOnPolicy(ClaimFacts claim, PolicyFacts policy) {
    return policy.memberNumbers().contains(claim.memberId())
        ? Assessment.pass(MEMBER_ON_POLICY, "The member is insured under the policy.")
        : Assessment.fail(MEMBER_ON_POLICY, "The member is not insured under the policy.");
  }

  static Assessment benefitCovered(
      ClaimFacts claim, Optional<List<TermsSnapshot.BenefitTerms>> path) {
    return path.isPresent()
        ? Assessment.pass(BENEFIT_COVERED, "The benefit is covered by the policy.")
        : Assessment.fail(BENEFIT_COVERED, "The benefit is not covered by the policy.");
  }

  static Assessment waitingPeriod(ClaimFacts claim, PolicyFacts policy, Set<String> codes) {
    Optional<TermsSnapshot.WaitingRuleTerms> rule =
        WaitingRuleResolver.resolve(
            policy.terms().waitingRules(),
            claim.cause(),
            claim.surgical(),
            claim.conditionTags(),
            codes);
    if (rule.isEmpty()) {
      return Assessment.pass(WAITING_PERIOD_ELAPSED, "No waiting period applies.");
    }
    LocalDate eligibleFrom = policy.coverStart().plusDays(rule.get().days());
    return claim.incidentDate().isBefore(eligibleFrom)
        ? Assessment.fail(
            WAITING_PERIOD_ELAPSED,
            "Waiting period " + rule.get().code() + " runs until " + eligibleFrom + ".")
        : Assessment.pass(
            WAITING_PERIOD_ELAPSED, "Waiting period " + rule.get().code() + " has elapsed.");
  }

  static Assessment filingDeadline(ClaimFacts claim, PolicyFacts policy, LocalDate today) {
    LocalDate last = claim.incidentDate().plusDays(policy.terms().filingDeadlineDays());
    return today.isAfter(last)
        ? Assessment.fail(FILING_DEADLINE, "The claim was filed after the filing deadline.")
        : Assessment.pass(FILING_DEADLINE, "The claim is filed within the filing deadline.");
  }

  static Assessment requiredDocuments(TermsSnapshot.BenefitTerms benefit, ClaimFacts claim) {
    List<String> missing =
        benefit.requiredDocuments().stream()
            .filter(type -> !claim.documentTypes().contains(type))
            .sorted()
            .collect(Collectors.toList());
    return missing.isEmpty()
        ? Assessment.pass(REQUIRED_DOCUMENTS, "All required documents are attached.")
        : Assessment.fail(
            REQUIRED_DOCUMENTS, "Missing required documents: " + String.join(", ", missing) + ".");
  }

  static Assessment amountPositive(ClaimFacts claim) {
    return claim.amountClaimed().signum() > 0
        ? Assessment.pass(AMOUNT_POSITIVE, "The amount claimed is above zero.")
        : Assessment.fail(AMOUNT_POSITIVE, "The amount claimed must be above zero.");
  }

  /** True if no assessment failed. */
  public static boolean accepted(List<Assessment> assessments) {
    return assessments.stream().noneMatch(a -> a.result() == AssessmentResult.FAIL);
  }
}
