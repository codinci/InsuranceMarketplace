package com.example.insurance.policyservice.policy.domain;

/**
 * Policy lifecycle (PLAN 7): PENDING_PAYMENT until paid (M4b), then ACTIVE, then EXPIRED or
 * CANCELLED. An unpaid policy is cancelled after 24 hours (M4b).
 */
public enum PolicyStatus {
  PENDING_PAYMENT,
  ACTIVE,
  EXPIRED,
  CANCELLED
}
