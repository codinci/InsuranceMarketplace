package com.example.insurance.claimservice.adjudication;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.insurance.claimservice.adjudication.LimitEvaluator.Outcome;
import com.example.insurance.claimservice.adjudication.LimitEvaluator.RunningLimit;
import com.example.insurance.claimservice.adjudication.LimitEvaluator.Usage;
import com.example.insurance.events.policy.TermsSnapshot.BenefitTerms;
import com.example.insurance.events.policy.TermsSnapshot.CopayTerms;
import com.example.insurance.events.policy.TermsSnapshot.LimitTerms;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * The limit evaluator (PLAN 9.2, D32) as a table: the two worked examples from the PLAN, each kind
 * of limit and scope, the count limit, both co-pays, the order of the rules, and rounding.
 */
class LimitEvaluatorTest {

  @Test
  void aClaimWithNoLimitsIsPaidInFull() {
    Outcome outcome = evaluate(List.of(node("OP", null, null)), "1500.00", Map.of());

    assertThat(outcome.eligible()).isEqualByComparingTo("1500.00");
    assertThat(outcome.payable()).isEqualByComparingTo("1500.00");
    assertThat(outcome.blockedBy()).isEmpty();
    assertThat(outcome.nothingPayable()).isFalse();
  }

  @Test
  void insurerAExampleAPerVisitCapOf2000LimitsA3000ConsultationTo2000() {
    BenefitTerms consult =
        node("CONSULT", limit(null, "PER_YEAR", "PER_MEMBER", "2000.00", null), null);

    Outcome outcome = evaluate(List.of(node("OP", null, null), consult), "3000.00", Map.of());

    assertThat(outcome.payable()).isEqualByComparingTo("2000.00");
  }

  @Test
  void insurerBExampleAFixedCopayOf300TakesA1500ConsultationTo1200() {
    BenefitTerms consult = node("CONSULT", null, copay("FIXED_PER_VISIT", "300"));

    Outcome outcome = evaluate(List.of(node("OP", null, null), consult), "1500.00", Map.of());

    assertThat(outcome.eligible()).isEqualByComparingTo("1500.00");
    assertThat(outcome.payable()).isEqualByComparingTo("1200.00");
  }

  @Test
  void aPercentCopayTakesThatFractionOff() {
    Outcome outcome =
        evaluate(List.of(node("OP", null, copay("PERCENT", "0.10"))), "2000.00", Map.of());

    assertThat(outcome.payable()).isEqualByComparingTo("1800.00");
  }

  @Test
  void aCopayLargerThanTheAmountLeavesNothing() {
    Outcome outcome =
        evaluate(List.of(node("OP", null, copay("FIXED_PER_VISIT", "5000"))), "1000.00", Map.of());

    assertThat(outcome.payable()).isEqualByComparingTo("0.00");
    assertThat(outcome.nothingPayable()).isTrue();
  }

  @Test
  void theDeepestCopayInTheChainApplies() {
    BenefitTerms parent = node("OP", null, copay("PERCENT", "0.20"));
    BenefitTerms child = node("CONSULT", null, copay("FIXED_PER_VISIT", "100"));

    assertThat(evaluate(List.of(parent, child), "1000.00", Map.of()).payable())
        .isEqualByComparingTo("900.00");
  }

  @Test
  void anUnknownCopayTypeIsIgnored() {
    Outcome outcome =
        evaluate(List.of(node("OP", null, copay("SOMETHING_NEW", "50"))), "1000.00", Map.of());

    assertThat(outcome.payable()).isEqualByComparingTo("1000.00");
  }

  @Test
  void aCopayWithoutAValueIsIgnored() {
    Outcome outcome =
        evaluate(List.of(node("OP", null, new CopayTerms("PERCENT", null))), "1000.00", Map.of());

    assertThat(outcome.payable()).isEqualByComparingTo("1000.00");
  }

  @Test
  void theRemainingAnnualLimitCapsTheClaim() {
    BenefitTerms op = node("OP", limit("10000", "PER_YEAR", "SHARED", null, null), null);

    Outcome outcome =
        evaluate(List.of(op), "3000.00", Map.of("OP", new Usage(new BigDecimal("9000.00"), 4)));

    assertThat(outcome.eligible()).isEqualByComparingTo("1000.00");
    assertThat(outcome.payable()).isEqualByComparingTo("1000.00");
  }

  @Test
  void aLimitThatIsUsedUpLeavesNothingPayable() {
    BenefitTerms op = node("OP", limit("10000", "PER_YEAR", "SHARED", null, null), null);

    Outcome outcome =
        evaluate(List.of(op), "3000.00", Map.of("OP", new Usage(new BigDecimal("12000.00"), 9)));

    assertThat(outcome.eligible()).isEqualByComparingTo("0.00");
    assertThat(outcome.nothingPayable()).isTrue();
  }

  @Test
  void theTightestLimitOnTheChainWins() {
    BenefitTerms parent = node("IP", limit("10000", "PER_YEAR", "SHARED", null, null), null);
    BenefitTerms child = node("SURGERY", limit("4000", "PER_YEAR", "PER_MEMBER", null, null), null);

    Outcome outcome =
        evaluate(
            List.of(parent, child),
            "3000.00",
            Map.of("IP", new Usage(new BigDecimal("9500.00"), 1)));

    assertThat(outcome.eligible()).isEqualByComparingTo("500.00");
  }

  @Test
  void aPerClaimCapLimitsOneClaimAndIgnoresAnyRunningTotal() {
    BenefitTerms capped = node("OP", limit("2500", "PER_CLAIM", "SHARED", null, null), null);

    Outcome outcome =
        evaluate(List.of(capped), "4000.00", Map.of("OP", new Usage(new BigDecimal("99999"), 99)));

    assertThat(outcome.eligible()).isEqualByComparingTo("2500.00");
    assertThat(LimitEvaluator.runningLimits(List.of(capped))).isEmpty();
  }

  @Test
  void aVisitCapAppliesWithoutARunningTotal() {
    BenefitTerms op = node("OP", limit(null, "PER_CLAIM", "SHARED", "1800.00", null), null);

    assertThat(evaluate(List.of(op), "2500.00", Map.of()).eligible())
        .isEqualByComparingTo("1800.00");
  }

  @Test
  void aCountLimitThatIsReachedBlocksTheClaim() {
    BenefitTerms op = node("OP", limit("10000", "PER_YEAR", "SHARED", null, 2), null);

    Outcome blocked =
        evaluate(List.of(op), "500.00", Map.of("OP", new Usage(new BigDecimal("100.00"), 2)));
    Outcome allowed =
        evaluate(List.of(op), "500.00", Map.of("OP", new Usage(new BigDecimal("100.00"), 1)));

    assertThat(blocked.blockedBy()).contains(LimitEvaluator.COUNT_LIMIT_REACHED);
    assertThat(blocked.nothingPayable()).isTrue();
    assertThat(allowed.blockedBy()).isEmpty();
    assertThat(allowed.payable()).isEqualByComparingTo("500.00");
  }

  @Test
  void aCountLimitWithoutAnAmountOnlyCountsVisits() {
    BenefitTerms op = node("OP", limit(null, "PER_YEAR", "SHARED", null, 3), null);

    Outcome outcome = evaluate(List.of(op), "800.00", Map.of("OP", new Usage(BigDecimal.ZERO, 1)));

    assertThat(outcome.payable()).isEqualByComparingTo("800.00");
  }

  @Test
  void theCopayComesAfterTheLimits() {
    BenefitTerms op =
        node("OP", limit("1000", "PER_YEAR", "SHARED", null, null), copay("PERCENT", "0.10"));

    // The limit caps 3000 to 1000 first; the co-pay then takes 10% of that.
    assertThat(evaluate(List.of(op), "3000.00", Map.of()).payable()).isEqualByComparingTo("900.00");
  }

  @Test
  void amountsRoundHalfUpToTwoPlaces() {
    Outcome outcome =
        evaluate(List.of(node("OP", null, copay("PERCENT", "0.10"))), "100.05", Map.of());

    // 100.05 x 0.90 = 90.045, which rounds half up to 90.05.
    assertThat(outcome.payable()).isEqualTo(new BigDecimal("90.05"));
  }

  @Test
  void theRunningLimitsAreThePerYearNodesInChainOrderWithTheirScope() {
    BenefitTerms parent = node("IP", limit("10000", "PER_YEAR", "SHARED", null, null), null);
    BenefitTerms child = node("SURGERY", limit("4000", "PER_YEAR", "PER_MEMBER", null, 2), null);
    BenefitTerms plain = node("LEAF", null, null);

    List<RunningLimit> limits = LimitEvaluator.runningLimits(List.of(parent, child, plain));

    assertThat(limits)
        .containsExactly(
            new RunningLimit("IP", false, false), new RunningLimit("SURGERY", true, true));
  }

  private static Outcome evaluate(
      List<BenefitTerms> chain, String claimed, Map<String, Usage> usage) {
    return LimitEvaluator.evaluate(chain, new BigDecimal(claimed), usage);
  }

  private static BenefitTerms node(String code, LimitTerms limit, CopayTerms copay) {
    return new BenefitTerms(code, "SERVICE", limit, copay, List.of(), false, List.of());
  }

  private static LimitTerms limit(
      String amount, String period, String scope, String maxPerVisit, Integer maxCount) {
    return new LimitTerms(
        amount == null ? null : new BigDecimal(amount),
        period,
        scope,
        maxPerVisit == null ? null : new BigDecimal(maxPerVisit),
        maxCount);
  }

  private static CopayTerms copay(String type, String value) {
    return new CopayTerms(type, new BigDecimal(value));
  }
}
