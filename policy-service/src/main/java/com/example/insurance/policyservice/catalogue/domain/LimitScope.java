package com.example.insurance.policyservice.catalogue.domain;

/** Who shares a limit (PLAN 5.3). */
public enum LimitScope {
  /** One pot for the whole policy. */
  SHARED,
  /** One pot per covered member. */
  PER_MEMBER
}
