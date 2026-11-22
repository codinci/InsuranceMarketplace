package com.example.insurance.claimservice.rules;

import com.example.insurance.claimservice.domain.AssessmentResult;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * The soft flags (PLAN 9.1): signs that a claim deserves a closer look from the reviewer. A flag
 * never rejects a claim; it only routes it to the top of the queue. Pure functions of the facts and
 * the configured thresholds. Only flags that are raised are returned.
 */
public final class SoftFlags {

  public static final String POSSIBLE_DUPLICATE = "POSSIBLE_DUPLICATE";
  public static final String HIGH_AMOUNT = "HIGH_AMOUNT";
  public static final String EARLY_IN_COVER = "EARLY_IN_COVER";
  public static final String FREQUENT_CLAIMS = "FREQUENT_CLAIMS";

  private SoftFlags() {
    // Static rules only; not instantiable.
  }

  /** The configured thresholds. */
  public record Thresholds(BigDecimal highAmount, int earlyCoverDays, int frequentClaimsCount) {}

  /**
   * What the flags need to know. {@code duplicates} counts other claims for the same member,
   * benefit, provider, incident date and amount. {@code recentClaims} counts the member's other
   * claims within the configured window.
   */
  public record Facts(
      BigDecimal amountClaimed,
      LocalDate incidentDate,
      LocalDate coverStart,
      long duplicates,
      long recentClaims) {}

  /** The flags this claim raises, in a fixed order. Empty when none. */
  public static List<Assessment> evaluate(Facts facts, Thresholds thresholds) {
    List<Assessment> flags = new ArrayList<>();
    if (facts.duplicates() > 0) {
      flags.add(
          flag(
              POSSIBLE_DUPLICATE,
              "Another claim has the same member, benefit, provider, date and amount."));
    }
    if (facts.amountClaimed().compareTo(thresholds.highAmount()) > 0) {
      flags.add(flag(HIGH_AMOUNT, "The amount claimed is above " + thresholds.highAmount() + "."));
    }
    if (facts.incidentDate().isBefore(facts.coverStart().plusDays(thresholds.earlyCoverDays()))) {
      flags.add(
          flag(
              EARLY_IN_COVER,
              "The incident is within "
                  + thresholds.earlyCoverDays()
                  + " days of the start of cover."));
    }
    // The claim being checked counts as one of the member's claims in the window.
    if (facts.recentClaims() + 1 >= thresholds.frequentClaimsCount()) {
      flags.add(
          flag(
              FREQUENT_CLAIMS,
              "The member has filed "
                  + thresholds.frequentClaimsCount()
                  + " or more claims recently."));
    }
    return List.copyOf(flags);
  }

  private static Assessment flag(String ruleCode, String detail) {
    return new Assessment(ruleCode, AssessmentResult.FLAG, detail);
  }
}
