package com.example.insurance.claimservice.domain;

/**
 * Where a claim is (PLAN 7). Transitions live in {@code statemachine.ClaimStateMachine}, which is
 * the only place a status may change (CLAUDE.md). The adjudication states are reached in M6.
 */
public enum ClaimStatus {
  DRAFT,
  SUBMITTED,
  UNDER_REVIEW,
  INFO_REQUESTED,
  REJECTED,
  APPROVED,
  PAYOUT_PENDING,
  PAID,
  PAYOUT_FAILED,
  WITHDRAWN
}
