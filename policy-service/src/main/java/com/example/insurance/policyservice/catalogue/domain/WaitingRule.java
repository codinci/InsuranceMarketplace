package com.example.insurance.policyservice.catalogue.domain;

/** A waiting period (PLAN 5.3). Highest precedence wins, then the longest (D31). */
public record WaitingRule(String code, int days, Integer precedence, WaitingPredicate when) {

  public WaitingRule {
    precedence = precedence == null ? Integer.valueOf(0) : precedence;
  }
}
