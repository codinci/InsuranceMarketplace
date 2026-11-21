package com.example.insurance.paymentservice.domain;

/**
 * Where a payment is (PLAN 7). {@code PENDING} and {@code FAILED} can still be paid; {@code
 * SUCCEEDED} and {@code EXPIRED} cannot change.
 */
public enum PaymentStatus {
  PENDING,
  SUCCEEDED,
  FAILED,
  EXPIRED
}
