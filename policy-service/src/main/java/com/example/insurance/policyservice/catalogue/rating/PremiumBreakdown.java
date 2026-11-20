package com.example.insurance.policyservice.catalogue.rating;

import java.math.BigDecimal;
import java.util.List;

/**
 * How an annual premium was built (PLAN 8.1). Every line is rounded HALF_UP to 2 places and the
 * annual amount is the sum of the rounded lines, so the breakdown always adds up (ADR-003).
 */
public record PremiumBreakdown(
    List<ModulePremium> modules,
    BigDecimal subtotal,
    List<ChargeAmount> adjustments,
    BigDecimal adjustedSubtotal,
    List<ChargeAmount> levies,
    BigDecimal annualPremium,
    String currency) {

  public PremiumBreakdown {
    modules = List.copyOf(modules);
    adjustments = List.copyOf(adjustments);
    levies = List.copyOf(levies);
  }

  /** The premium of one selected module option. */
  public record ModulePremium(String module, String option, BigDecimal amount) {}

  /** The amount of one adjustment or levy. */
  public record ChargeAmount(String code, BigDecimal amount) {}
}
