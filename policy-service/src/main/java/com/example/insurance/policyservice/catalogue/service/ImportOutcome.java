package com.example.insurance.policyservice.catalogue.service;

import com.example.insurance.policyservice.catalogue.dto.AdminPlanResponse;

/** What an import did, and the stored plan unless it was a dry run. */
public record ImportOutcome(Result result, AdminPlanResponse plan) {

  /** The three ways an import can succeed. */
  public enum Result {
    VALIDATED,
    CREATED,
    REPLACED
  }
}
