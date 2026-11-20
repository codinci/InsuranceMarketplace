package com.example.insurance.policyservice.catalogue.domain;

/** How a member's rating age is computed from the policy start date (PLAN 5.3). */
public enum AgeBasis {
  /** Completed years on the start date. */
  AT_START,
  /** The age the member turns at their next birthday on or after the start date. */
  NEXT_BIRTHDAY
}
