package com.example.insurance.policyservice.policy.domain;

/** A quote is OPEN until a policy is bought from it. Expiry is computed from validUntil. */
public enum QuoteStatus {
  OPEN,
  USED
}
