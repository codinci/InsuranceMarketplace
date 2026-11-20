package com.example.insurance.policyservice.policy.domain;

import java.util.Locale;

/**
 * Human-readable numbers (PLAN 3): {@code POL-ACACIA-2026-000123} for a policy and {@code
 * ACA-000123-01} for its first member. Both come from one database sequence value.
 */
public final class PolicyNumbers {

  private PolicyNumbers() {}

  public static String policyNumber(String insurerCode, int year, long sequence) {
    return String.format(Locale.ROOT, "POL-%s-%d-%06d", insurerCode, year, sequence);
  }

  /** {@code position} starts at 0 for the first member; the number shows it from 01. */
  public static String memberNumber(String insurerCode, long sequence, int position) {
    String letters = insurerCode.replaceAll("[^A-Z0-9]", "");
    String prefix = letters.substring(0, Math.min(3, letters.length()));
    return String.format(Locale.ROOT, "%s-%06d-%02d", prefix, sequence, position + 1);
  }
}
