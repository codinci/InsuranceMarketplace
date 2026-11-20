package com.example.insurance.policyservice.catalogue.domain;

/** Benefit payment style. {@code FIXED_SUM} is schema-only in v1 and rejected (D18). */
public enum BenefitKind {
  REIMBURSEMENT,
  FIXED_SUM
}
