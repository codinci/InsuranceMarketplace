package com.example.insurance.claimservice.rules;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.insurance.claimservice.domain.AssessmentResult;
import com.example.insurance.claimservice.rules.SoftFlags.Facts;
import com.example.insurance.claimservice.rules.SoftFlags.Thresholds;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;

/** The soft flags (PLAN 9.1) as a table: each one at, just below and just over its threshold. */
class SoftFlagsTest {

  private static final LocalDate START = LocalDate.of(2026, 1, 1);
  private static final LocalDate LATER = LocalDate.of(2026, 6, 1);
  private static final Thresholds THRESHOLDS = new Thresholds(new BigDecimal("100000"), 30, 3);

  @Test
  void aPlainClaimRaisesNoFlag() {
    assertThat(flags(facts("5000", LATER, 0, 0))).isEmpty();
  }

  @Test
  void aDuplicateIsFlaggedWhenAnotherClaimMatches() {
    assertThat(flags(facts("5000", LATER, 1, 0))).containsExactly(SoftFlags.POSSIBLE_DUPLICATE);
    assertThat(flags(facts("5000", LATER, 4, 0))).containsExactly(SoftFlags.POSSIBLE_DUPLICATE);
  }

  @Test
  void aHighAmountIsFlaggedOnlyAboveTheThreshold() {
    assertThat(flags(facts("100000.00", LATER, 0, 0))).isEmpty();
    assertThat(flags(facts("100000.01", LATER, 0, 0))).containsExactly(SoftFlags.HIGH_AMOUNT);
  }

  @Test
  void anIncidentWithinThirtyDaysOfTheStartIsFlagged() {
    assertThat(flags(facts("5000", START.plusDays(29), 0, 0)))
        .containsExactly(SoftFlags.EARLY_IN_COVER);
    assertThat(flags(facts("5000", START.plusDays(30), 0, 0))).isEmpty();
    assertThat(flags(facts("5000", START, 0, 0))).containsExactly(SoftFlags.EARLY_IN_COVER);
  }

  @Test
  void frequentClaimsCountTheClaimBeingCheckedAsOne() {
    // Threshold 3, this claim included: two other recent claims make three.
    assertThat(flags(facts("5000", LATER, 0, 1))).isEmpty();
    assertThat(flags(facts("5000", LATER, 0, 2))).containsExactly(SoftFlags.FREQUENT_CLAIMS);
    assertThat(flags(facts("5000", LATER, 0, 7))).containsExactly(SoftFlags.FREQUENT_CLAIMS);
  }

  @Test
  void severalFlagsAreReturnedInAFixedOrder() {
    List<String> codes = flags(facts("200000", START.plusDays(5), 2, 5));

    assertThat(codes)
        .containsExactly(
            SoftFlags.POSSIBLE_DUPLICATE,
            SoftFlags.HIGH_AMOUNT,
            SoftFlags.EARLY_IN_COVER,
            SoftFlags.FREQUENT_CLAIMS);
  }

  @Test
  void everyFlagIsAFlagResultWithAnExplanation() {
    List<Assessment> raised =
        SoftFlags.evaluate(facts("200000", START.plusDays(5), 2, 5), THRESHOLDS);

    assertThat(raised)
        .allSatisfy(a -> {
          assertThat(a.result()).isEqualTo(AssessmentResult.FLAG);
          assertThat(a.detail()).isNotBlank();
        });
  }

  private static List<String> flags(Facts facts) {
    return SoftFlags.evaluate(facts, THRESHOLDS).stream().map(Assessment::ruleCode).toList();
  }

  private static Facts facts(String amount, LocalDate incident, long duplicates, long recent) {
    return new Facts(new BigDecimal(amount), incident, START, duplicates, recent);
  }
}
