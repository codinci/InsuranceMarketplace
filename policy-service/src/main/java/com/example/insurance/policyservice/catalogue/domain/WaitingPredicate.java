package com.example.insurance.policyservice.catalogue.domain;

import java.util.List;

/**
 * When a waiting rule applies (D31): every present field must match; inside a list any one entry is
 * enough; a benefit entry also matches its descendants. Claim-service evaluates it (M5).
 */
public record WaitingPredicate(
    String cause, Boolean surgical, List<String> conditionTags, List<String> benefits) {

  public WaitingPredicate {
    conditionTags = conditionTags == null ? List.of() : List.copyOf(conditionTags);
    benefits = benefits == null ? List.of() : List.copyOf(benefits);
  }
}
