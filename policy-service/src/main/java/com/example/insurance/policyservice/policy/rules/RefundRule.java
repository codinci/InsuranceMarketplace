package com.example.insurance.policyservice.policy.rules;

import com.example.insurance.events.policy.TermsSnapshot.CancellationTerms;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

/**
 * The plan's refund rule, read from the policy's terms snapshot (D7, ADR-021). A pure function: no
 * database, no clock, so every branch is tested with plain values.
 */
public final class RefundRule {

  /** Refund for the unused days of cover. */
  public static final String PRO_RATA = "PRO_RATA";

  /** No refund on cancellation. */
  public static final String NONE = "NONE";

  private RefundRule() {
    // Static rule only; not instantiable.
  }

  /**
   * What a cancellation refunds.
   *
   * @param terms the cancellation part of the terms snapshot; {@code null} gives no refund
   * @param annualPremium the premium paid for the year of cover
   * @param startDate the first day of cover
   * @param endDate the last day of cover
   * @param requestDate the day the holder asked to cancel
   * @param effectiveDate the first day without cover
   * @param claimCount claims submitted against the policy so far
   * @param paid whether the premium was paid
   */
  public record Cancellation(
      CancellationTerms terms,
      BigDecimal annualPremium,
      LocalDate startDate,
      LocalDate endDate,
      LocalDate requestDate,
      LocalDate effectiveDate,
      long claimCount,
      boolean paid) {}

  /** The refund in the policy currency, two decimals, HALF_UP. Zero when the rule gives none. */
  public static BigDecimal refund(Cancellation c) {
    BigDecimal none = BigDecimal.ZERO.setScale(2);
    if (!c.paid() || c.terms() == null || NONE.equals(c.terms().refundModel())) {
      return none;
    }
    if (!PRO_RATA.equals(c.terms().refundModel())) {
      throw new IllegalStateException("Unknown refund model " + c.terms().refundModel() + ".");
    }
    if (Boolean.TRUE.equals(c.terms().noRefundIfClaims()) && c.claimCount() > 0) {
      return none;
    }
    Integer months = c.terms().noRefundAfterMonths();
    if (months != null && !c.requestDate().isBefore(c.startDate().plusMonths(months))) {
      return none;
    }
    long coverDays = ChronoUnit.DAYS.between(c.startDate(), c.endDate()) + 1;
    long unusedDays = ChronoUnit.DAYS.between(c.effectiveDate(), c.endDate()) + 1;
    unusedDays = Math.clamp(unusedDays, 0, coverDays);
    return c.annualPremium()
        .multiply(BigDecimal.valueOf(unusedDays))
        .divide(BigDecimal.valueOf(coverDays), 2, RoundingMode.HALF_UP);
  }
}
