package com.example.insurance.claimservice.adjudication;

import com.example.insurance.events.policy.TermsSnapshot.BenefitTerms;
import com.example.insurance.events.policy.TermsSnapshot.CopayTerms;
import com.example.insurance.events.policy.TermsSnapshot.LimitTerms;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * What can be paid for a claim, from the benefit chain of the policy's terms (PLAN 9.2, D32).
 * Generic: no benefit, insurer or plan is named here. Pure functions of the chain, the claimed
 * amount and the running totals, so every rule is tested as a table. Money is {@link BigDecimal},
 * KES with two places, HALF_UP (CLAUDE.md).
 *
 * <p>Order of the rules, root to claimed benefit: every limited node caps the amount, then the
 * co-pay of the deepest node that has one is applied last.
 */
public final class LimitEvaluator {

  public static final String COUNT_LIMIT_REACHED = "COUNT_LIMIT_REACHED";

  private static final String PER_YEAR = "PER_YEAR";
  private static final String PER_MEMBER = "PER_MEMBER";
  private static final String PERCENT = "PERCENT";
  private static final String FIXED_PER_VISIT = "FIXED_PER_VISIT";

  private LimitEvaluator() {
    // Static rules only; not instantiable.
  }

  /** What has been used against one limit so far. */
  public record Usage(BigDecimal amountUsed, int countUsed) {

    public static final Usage NONE = new Usage(BigDecimal.ZERO, 0);
  }

  /**
   * A limit that keeps a running total across claims (PER_YEAR). {@code perMember} says whether the
   * total belongs to one member or is shared by the policy. {@code countsVisits} is true when the
   * limit also caps the number of claims.
   */
  public record RunningLimit(String benefitCode, boolean perMember, boolean countsVisits) {}

  /**
   * The result of an evaluation: {@code eligible} after the limits, {@code payable} after the
   * co-pay, and {@code blockedBy} when a count limit is already used up.
   */
  public record Outcome(BigDecimal eligible, BigDecimal payable, Optional<String> blockedBy) {

    /**
     * True when nothing can be paid, either because a count limit is reached or the amount is zero.
     */
    public boolean nothingPayable() {
      return blockedBy.isPresent() || payable.signum() <= 0;
    }
  }

  /** The limits on the chain that keep a running total, in chain order. */
  public static List<RunningLimit> runningLimits(List<BenefitTerms> chain) {
    List<RunningLimit> limits = new ArrayList<>();
    for (BenefitTerms node : chain) {
      LimitTerms limit = node.limit();
      if (limit != null && PER_YEAR.equals(limit.period())) {
        limits.add(
            new RunningLimit(
                node.code(), PER_MEMBER.equals(limit.scope()), limit.maxCount() != null));
      }
    }
    return List.copyOf(limits);
  }

  /**
   * Evaluates a claim. {@code usage} holds the running totals by benefit code for the limits
   * returned by {@link #runningLimits}; a missing entry means nothing used yet.
   */
  public static Outcome evaluate(
      List<BenefitTerms> chain, BigDecimal claimed, Map<String, Usage> usage) {
    BigDecimal eligible = money(claimed);
    Optional<String> blockedBy = Optional.empty();
    CopayTerms copay = null;

    for (BenefitTerms node : chain) {
      LimitTerms limit = node.limit();
      if (limit != null) {
        if (PER_YEAR.equals(limit.period())) {
          Usage used = usage.getOrDefault(node.code(), Usage.NONE);
          if (limit.amount() != null) {
            eligible =
                eligible.min(limit.amount().subtract(used.amountUsed()).max(BigDecimal.ZERO));
          }
          if (limit.maxCount() != null && used.countUsed() >= limit.maxCount()) {
            blockedBy = Optional.of(COUNT_LIMIT_REACHED);
          }
        } else if (limit.amount() != null) {
          // PER_CLAIM: caps this claim only and keeps no running total (D32).
          eligible = eligible.min(limit.amount());
        }
        if (limit.maxPerVisit() != null) {
          eligible = eligible.min(limit.maxPerVisit());
        }
      }
      if (node.copay() != null) {
        copay = node.copay();
      }
    }

    eligible = money(eligible);
    return new Outcome(eligible, money(afterCopay(eligible, copay)), blockedBy);
  }

  private static BigDecimal afterCopay(BigDecimal eligible, CopayTerms copay) {
    if (copay == null || copay.value() == null) {
      return eligible;
    }
    if (PERCENT.equals(copay.type())) {
      return eligible.multiply(BigDecimal.ONE.subtract(copay.value()));
    }
    if (FIXED_PER_VISIT.equals(copay.type())) {
      return eligible.subtract(copay.value()).max(BigDecimal.ZERO);
    }
    return eligible;
  }

  private static BigDecimal money(BigDecimal value) {
    return value.setScale(2, RoundingMode.HALF_UP);
  }
}
