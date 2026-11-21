package com.example.insurance.claimservice.domain;

/** The outcome of one automatic check: PASS, FAIL (rejects the claim), or FLAG (for review, M6). */
public enum AssessmentResult {
  PASS,
  FAIL,
  FLAG
}
