package com.example.insurance.claimservice.adjudication;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

/**
 * Which policy year an incident falls in, counted from the start of cover (PLAN 7). A policy lasts
 * one year, so every claim today is in year 1; the key is kept in the utilisation table so renewals
 * can start a new year without a schema change.
 */
public final class PolicyYears {

  private PolicyYears() {
    // Static rules only; not instantiable.
  }

  /** 1 for the first twelve months from {@code coverStart}, 2 for the next, and so on. */
  public static int of(LocalDate coverStart, LocalDate incidentDate) {
    long years = ChronoUnit.YEARS.between(coverStart, incidentDate);
    return (int) Math.max(0, years) + 1;
  }
}
