package com.example.insurance.claimservice.rules;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.insurance.claimservice.domain.AssessmentResult;
import com.example.insurance.claimservice.domain.Cause;
import com.example.insurance.events.policy.TermsSnapshot;
import com.example.insurance.events.policy.TermsSnapshot.BenefitTerms;
import com.example.insurance.events.policy.TermsSnapshot.ModuleTerms;
import com.example.insurance.events.policy.TermsSnapshot.WaitingRuleTerms;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;

/**
 * The automatic checks (PLAN 9), table-style: a claim that passes everything, then one variant per
 * rule that breaks exactly that rule. Dates are fixed so the table does not depend on the clock.
 */
class HardChecksTest {

  private static final LocalDate COVER_START = LocalDate.of(2026, 1, 1);
  private static final LocalDate COVER_END = LocalDate.of(2026, 12, 31);
  private static final LocalDate TODAY = LocalDate.of(2026, 6, 20);
  private static final LocalDate INCIDENT = LocalDate.of(2026, 6, 15);

  private static final BenefitTerms CONSULT =
      new BenefitTerms("OP-CONSULT", "SERVICE", null, null, List.of("RECEIPT"), false, List.of());
  private static final BenefitTerms OUTPATIENT =
      new BenefitTerms("OUTPATIENT", "CATEGORY", null, null, List.of(), false, List.of(CONSULT));
  private static final TermsSnapshot TERMS =
      new TermsSnapshot(
          1,
          "ACACIA",
          null,
          "STANDARD",
          1,
          "KES",
          "NEXT_BIRTHDAY",
          List.of(new ModuleTerms("OUTPATIENT", "OP-40K", OUTPATIENT)),
          List.of(
              new WaitingRuleTerms(
                  "GENERAL_ILLNESS", 30, 0, "ILLNESS", null, List.of(), List.of())),
          List.of(),
          90,
          null);

  @Test
  void aCompleteClaimPassesEveryCheck() {
    List<Assessment> results = evaluate(claim(), policy("ACTIVE", null), TODAY);

    assertThat(results).allMatch(a -> a.result() == AssessmentResult.PASS);
    assertThat(HardChecks.accepted(results)).isTrue();
  }

  @Test
  void aClaimOutsideCoverFailsPolicyInForce() {
    assertResult(
        claim().incident(COVER_START.minusDays(1)),
        policy("ACTIVE", null),
        HardChecks.POLICY_IN_FORCE,
        AssessmentResult.FAIL);
  }

  @Test
  void aCancelledPolicyCoversOnlyBeforeTheCancellationDate() {
    LocalDate cancelled = LocalDate.of(2026, 6, 1);

    assertResult(
        claim(), policy("CANCELLED", cancelled), HardChecks.POLICY_IN_FORCE, AssessmentResult.FAIL);
    assertResult(
        claim().incident(LocalDate.of(2026, 5, 1)),
        policy("CANCELLED", cancelled),
        HardChecks.POLICY_IN_FORCE,
        AssessmentResult.PASS);
  }

  @Test
  void aFutureIncidentFails() {
    assertResult(
        claim().incident(TODAY.plusDays(1)),
        policy("ACTIVE", null),
        HardChecks.INCIDENT_NOT_FUTURE,
        AssessmentResult.FAIL);
  }

  @Test
  void aMemberNotOnThePolicyFails() {
    assertResult(
        claim().member("M-9"),
        policy("ACTIVE", null),
        HardChecks.MEMBER_ON_POLICY,
        AssessmentResult.FAIL);
  }

  @Test
  void aBenefitNotInTheTermsFailsAndSkipsTheChecksThatNeedIt() {
    List<Assessment> results = evaluate(claim().benefit("DENTAL"), policy("ACTIVE", null), TODAY);

    assertThat(resultOf(results, HardChecks.BENEFIT_COVERED)).isEqualTo(AssessmentResult.FAIL);
    assertThat(results.stream().map(Assessment::ruleCode))
        .doesNotContain(HardChecks.WAITING_PERIOD_ELAPSED, HardChecks.REQUIRED_DOCUMENTS);
  }

  @Test
  void aClaimInsideTheWaitingPeriodFails() {
    // Cover starts 1 January; the 30-day illness rule runs to 31 January.
    assertResult(
        claim().incident(LocalDate.of(2026, 1, 20)),
        policy("ACTIVE", null),
        HardChecks.WAITING_PERIOD_ELAPSED,
        AssessmentResult.FAIL);
  }

  @Test
  void aClaimAfterTheWaitingPeriodPasses() {
    assertResult(
        claim().incident(LocalDate.of(2026, 2, 15)),
        policy("ACTIVE", null),
        HardChecks.WAITING_PERIOD_ELAPSED,
        AssessmentResult.PASS);
  }

  @Test
  void anAccidentHasNoWaitingPeriod() {
    assertResult(
        claim().incident(LocalDate.of(2026, 1, 2)).cause(Cause.ACCIDENT),
        policy("ACTIVE", null),
        HardChecks.WAITING_PERIOD_ELAPSED,
        AssessmentResult.PASS);
  }

  @Test
  void aClaimFiledLateFailsTheFilingDeadline() {
    assertThat(
            resultOf(
                evaluate(claim(), policy("ACTIVE", null), INCIDENT.plusDays(91)),
                HardChecks.FILING_DEADLINE))
        .isEqualTo(AssessmentResult.FAIL);
  }

  @Test
  void aClaimFiledOnTheLastDayPassesTheFilingDeadline() {
    assertThat(
            resultOf(
                evaluate(claim(), policy("ACTIVE", null), INCIDENT.plusDays(90)),
                HardChecks.FILING_DEADLINE))
        .isEqualTo(AssessmentResult.PASS);
  }

  @Test
  void aMissingRequiredDocumentFailsAndNamesIt() {
    Assessment check =
        evaluate(claim().documents(Set.of()), policy("ACTIVE", null), TODAY).stream()
            .filter(a -> a.ruleCode().equals(HardChecks.REQUIRED_DOCUMENTS))
            .findFirst()
            .orElseThrow();

    assertThat(check.result()).isEqualTo(AssessmentResult.FAIL);
    assertThat(check.detail()).contains("RECEIPT");
  }

  @Test
  void aZeroAmountFails() {
    assertResult(
        claim().amount(BigDecimal.ZERO),
        policy("ACTIVE", null),
        HardChecks.AMOUNT_POSITIVE,
        AssessmentResult.FAIL);
  }

  @Test
  void aRejectedClaimListsEveryFailingCheck() {
    List<Assessment> results =
        evaluate(
            claim().incident(COVER_START.minusDays(1)).member("M-9").documents(Set.of()),
            policy("ACTIVE", null),
            TODAY);

    assertThat(HardChecks.accepted(results)).isFalse();
    assertThat(
            results.stream()
                .filter(a -> a.result() == AssessmentResult.FAIL)
                .map(Assessment::ruleCode))
        .contains(
            HardChecks.POLICY_IN_FORCE, HardChecks.MEMBER_ON_POLICY, HardChecks.REQUIRED_DOCUMENTS);
  }

  @Test
  void theBenefitPathRunsFromTheRootToTheClaimedBenefit() {
    Optional<List<BenefitTerms>> path = BenefitPath.find(TERMS, "OUTPATIENT", "OP-CONSULT");

    assertThat(path).isPresent();
    assertThat(path.get())
        .extracting(BenefitTerms::code)
        .containsExactly("OUTPATIENT", "OP-CONSULT");
    assertThat(BenefitPath.find(TERMS, "DENTAL", "OP-CONSULT")).isEmpty();
    assertThat(BenefitPath.find(null, "OUTPATIENT", "OP-CONSULT")).isEmpty();
  }

  @Test
  void aBenefitFoundInALaterBranchIsStillFound() {
    BenefitTerms leaf =
        new BenefitTerms("LEAF", "SERVICE", null, null, List.of(), false, List.of());
    BenefitTerms middle =
        new BenefitTerms("MID", "CATEGORY", null, null, List.of(), false, List.of(leaf));
    BenefitTerms first =
        new BenefitTerms("FIRST", "SERVICE", null, null, List.of(), false, List.of());
    BenefitTerms root =
        new BenefitTerms("ROOT", "CATEGORY", null, null, List.of(), false, List.of(first, middle));
    TermsSnapshot terms =
        new TermsSnapshot(
            1,
            "ACACIA",
            null,
            "STANDARD",
            1,
            "KES",
            "NEXT_BIRTHDAY",
            List.of(new ModuleTerms("M", "O", root)),
            List.of(),
            List.of(),
            90,
            null);

    assertThat(BenefitPath.find(terms, "M", "LEAF"))
        .hasValueSatisfying(path ->
            assertThat(path).extracting(BenefitTerms::code).containsExactly("ROOT", "MID", "LEAF"));
  }

  @Test
  void theCodesOfAPathAreItsNodes() {
    assertThat(BenefitPath.codes(List.of(OUTPATIENT, CONSULT)))
        .containsExactlyInAnyOrder("OUTPATIENT", "OP-CONSULT");
  }

  private static List<Assessment> evaluate(
      ClaimBuilder claim, HardChecks.PolicyFacts policy, LocalDate today) {
    return HardChecks.evaluate(claim.build(), policy, today);
  }

  private static void assertResult(
      ClaimBuilder claim,
      HardChecks.PolicyFacts policy,
      String ruleCode,
      AssessmentResult expected) {
    assertThat(resultOf(evaluate(claim, policy, TODAY), ruleCode)).isEqualTo(expected);
  }

  private static AssessmentResult resultOf(List<Assessment> results, String ruleCode) {
    return results.stream()
        .filter(a -> a.ruleCode().equals(ruleCode))
        .findFirst()
        .orElseThrow()
        .result();
  }

  private static HardChecks.PolicyFacts policy(String status, LocalDate cancelEffective) {
    return new HardChecks.PolicyFacts(
        status, COVER_START, COVER_END, cancelEffective, Set.of("M-1"), TERMS);
  }

  /** A claim that passes every check; each test changes one field. */
  private static ClaimBuilder claim() {
    return new ClaimBuilder();
  }

  /** Builds claim facts one field at a time, so each table row shows only what it changes. */
  private static final class ClaimBuilder {
    private String member = "M-1";
    private String benefit = "OP-CONSULT";
    private Cause cause = Cause.ILLNESS;
    private LocalDate incident = INCIDENT;
    private BigDecimal amount = BigDecimal.TEN;
    private Set<String> documents = Set.of("RECEIPT");

    ClaimBuilder member(String value) {
      this.member = value;
      return this;
    }

    ClaimBuilder benefit(String value) {
      this.benefit = value;
      return this;
    }

    ClaimBuilder cause(Cause value) {
      this.cause = value;
      return this;
    }

    ClaimBuilder incident(LocalDate value) {
      this.incident = value;
      return this;
    }

    ClaimBuilder amount(BigDecimal value) {
      this.amount = value;
      return this;
    }

    ClaimBuilder documents(Set<String> value) {
      this.documents = value;
      return this;
    }

    HardChecks.ClaimFacts build() {
      return new HardChecks.ClaimFacts(
          member, "OUTPATIENT", benefit, cause, false, Set.of(), incident, amount, documents);
    }
  }
}
