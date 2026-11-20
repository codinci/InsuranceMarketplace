package com.example.insurance.policyservice.catalogue.domain;

import java.util.List;

/** The plan block of a plan document (PLAN 5.1, docs/catalogue-schema.md). */
public record PlanDefinition(
    String code,
    String name,
    int version,
    String currency,
    SalePeriod sale,
    AgeBasis ageBasis,
    Eligibility eligibility,
    List<ModuleDef> modules,
    List<WaitingRule> waitingRules,
    List<String> exclusions,
    ClaimRules claimRules,
    Cancellation cancellation,
    List<Charge> adjustments,
    List<Charge> levies) {

  public PlanDefinition {
    modules = modules == null ? List.of() : List.copyOf(modules);
    waitingRules = waitingRules == null ? List.of() : List.copyOf(waitingRules);
    exclusions = exclusions == null ? List.of() : List.copyOf(exclusions);
    adjustments = adjustments == null ? List.of() : List.copyOf(adjustments);
    levies = levies == null ? List.of() : List.copyOf(levies);
  }
}
