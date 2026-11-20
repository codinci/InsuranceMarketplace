package com.example.insurance.policyservice.catalogue.api;

import java.math.BigDecimal;
import java.util.List;

/** A server-computed premium and how it was built (PLAN 8.1, ADR-003). */
public record QuotedPrice(
    List<ModuleAmount> modules,
    BigDecimal subtotal,
    List<ChargeAmount> adjustments,
    BigDecimal adjustedSubtotal,
    List<ChargeAmount> levies,
    BigDecimal annualPremium,
    String currency) {

  public QuotedPrice {
    modules = List.copyOf(modules);
    adjustments = List.copyOf(adjustments);
    levies = List.copyOf(levies);
  }

  /** The premium of one selected module option. */
  public record ModuleAmount(String module, String option, BigDecimal amount) {}

  /** The amount of one adjustment or levy. */
  public record ChargeAmount(String code, BigDecimal amount) {}
}
