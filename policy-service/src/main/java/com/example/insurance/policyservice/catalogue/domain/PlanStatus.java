package com.example.insurance.policyservice.catalogue.domain;

/** Plan version lifecycle (PLAN 7, ADR-010): DRAFT, then PUBLISHED, then RETIRED. */
public enum PlanStatus {
  DRAFT,
  PUBLISHED,
  RETIRED
}
