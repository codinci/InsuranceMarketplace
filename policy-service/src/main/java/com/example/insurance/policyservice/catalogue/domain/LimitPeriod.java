package com.example.insurance.policyservice.catalogue.domain;

/** How a limit accumulates (PLAN 5.3, D32). */
public enum LimitPeriod {
  /** Running total for the policy year, which starts on the policy's start date (D30). */
  PER_YEAR,
  /** Caps each claim on its own, with no running total (D32). */
  PER_CLAIM
}
