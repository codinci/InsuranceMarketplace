package com.example.insurance.claimservice.rules;

import com.example.insurance.claimservice.domain.AssessmentResult;

/** One automatic check and its outcome, stored as a {@code ClaimAssessment} (PLAN 7). */
public record Assessment(String ruleCode, AssessmentResult result, String detail) {

  public static Assessment pass(String ruleCode, String detail) {
    return new Assessment(ruleCode, AssessmentResult.PASS, detail);
  }

  public static Assessment fail(String ruleCode, String detail) {
    return new Assessment(ruleCode, AssessmentResult.FAIL, detail);
  }
}
