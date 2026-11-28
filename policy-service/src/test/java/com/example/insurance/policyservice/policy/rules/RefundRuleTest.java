package com.example.insurance.policyservice.policy.rules;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.insurance.events.policy.TermsSnapshot.CancellationTerms;
import com.example.insurance.policyservice.policy.rules.RefundRule.Cancellation;
import java.math.BigDecimal;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/** The refund rule with both fictional insurers' terms (D7, ADR-021). */
class RefundRuleTest {

  /** Acacia: pro-rata, nothing after six months, nothing once a claim was made. */
  private static final CancellationTerms ACACIA = new CancellationTerms(30, "PRO_RATA", 6, true);

  /** Baobab: never a refund. */
  private static final CancellationTerms BAOBAB = new CancellationTerms(14, "NONE", null, null);

  /** Pro-rata with no month limit and claims allowed, to reach the plain proportion. */
  private static final CancellationTerms PLAIN = new CancellationTerms(0, "PRO_RATA", null, false);

  /** 365 days of cover at 100.00 a day. */
  private static final BigDecimal PREMIUM = new BigDecimal("36500.00");

  private static final LocalDate START = LocalDate.of(2027, 1, 1);
  private static final LocalDate END = LocalDate.of(2027, 12, 31);

  private static Cancellation cancel(
      CancellationTerms terms, LocalDate request, LocalDate effective, long claims, boolean paid) {
    return new Cancellation(terms, PREMIUM, START, END, request, effective, claims, paid);
  }

  @ParameterizedTest(name = "request {0}, effective {1}: refund {2}")
  @CsvSource({
    // Before cover starts: the whole year is unused.
    "2026-12-01, 2026-12-31, 36500.00",
    "2026-12-01, 2027-01-01, 36500.00",
    // The 30-day notice ends on 31 January: 335 days unused.
    "2027-01-01, 2027-01-31, 33500.00",
    // Last day before the six-month limit still refunds.
    "2027-06-30, 2027-07-30, 15500.00",
    // On the limit: nothing.
    "2027-07-01, 2027-07-31, 0.00",
  })
  void acaciaRefundsTheUnusedDaysUntilSixMonths(
      LocalDate request, LocalDate effective, BigDecimal expected) {
    assertThat(RefundRule.refund(cancel(ACACIA, request, effective, 0, true)))
        .isEqualByComparingTo(expected)
        .hasScaleOf(2);
  }

  @Test
  void acaciaRefundsNothingOnceAClaimWasSubmitted() {
    LocalDate request = LocalDate.of(2027, 2, 1);

    assertThat(RefundRule.refund(cancel(ACACIA, request, request.plusDays(30), 1, true)))
        .isEqualByComparingTo("0.00");
  }

  @Test
  void baobabNeverRefunds() {
    LocalDate request = LocalDate.of(2027, 1, 2);

    assertThat(RefundRule.refund(cancel(BAOBAB, request, request.plusDays(14), 0, true)))
        .isEqualByComparingTo("0.00")
        .hasScaleOf(2);
  }

  @Test
  void anUnpaidPolicyIsNeverRefunded() {
    assertThat(RefundRule.refund(cancel(ACACIA, START, START, 0, false)))
        .isEqualByComparingTo("0.00");
  }

  @Test
  void termsWithoutACancellationRuleRefundNothing() {
    assertThat(RefundRule.refund(cancel(null, START, START, 0, true))).isEqualByComparingTo("0.00");
  }

  @Test
  void claimsDoNotMatterWhenThePlanAllowsThem() {
    assertThat(RefundRule.refund(cancel(PLAIN, START, LocalDate.of(2027, 7, 2), 3, true)))
        .isEqualByComparingTo("18300.00");
  }

  @Test
  void anEffectiveDateAfterTheEndRefundsNothingRatherThanANegativeAmount() {
    assertThat(RefundRule.refund(cancel(PLAIN, END, END.plusDays(1), 0, true)))
        .isEqualByComparingTo("0.00");
  }

  @Test
  void theLastDayOfCoverIsOneUnusedDay() {
    assertThat(RefundRule.refund(cancel(PLAIN, END, END, 0, true))).isEqualByComparingTo("100.00");
  }

  @Test
  void aLeapYearSpreadsThePremiumOver366DaysAndRoundsHalfUp() {
    LocalDate start = LocalDate.of(2028, 1, 1);
    LocalDate end = LocalDate.of(2028, 12, 31);
    Cancellation c =
        new Cancellation(PLAIN, new BigDecimal("10000.00"), start, end, start, end, 0, true);

    // 10000.00 / 366 = 27.3224...: one unused day.
    assertThat(RefundRule.refund(c)).isEqualByComparingTo("27.32");
  }

  @Test
  void anUnknownModelFailsLoudly() {
    CancellationTerms odd = new CancellationTerms(0, "HALF", null, null);

    assertThatThrownBy(() -> RefundRule.refund(cancel(odd, START, START, 0, true)))
        .isInstanceOf(IllegalStateException.class);
  }
}
