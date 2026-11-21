package com.example.insurance.paymentservice.controller;

import com.example.insurance.paymentservice.exception.ApiException;
import com.example.insurance.paymentservice.exception.ErrorCode;

/**
 * The {@code Idempotency-Key} header (PLAN 11). A missing or oversized key is refused before any
 * work starts, so no retry can ever run without one.
 */
final class IdempotencyKeys {

  static final String HEADER = "Idempotency-Key";
  private static final int MAX_LENGTH = 80;

  private IdempotencyKeys() {}

  static String require(String key) {
    if (key == null || key.isBlank() || key.length() > MAX_LENGTH) {
      throw new ApiException(
          ErrorCode.IDEMPOTENCY_KEY_REQUIRED,
          "Send an Idempotency-Key header of 1 to 80 characters.");
    }
    return key;
  }
}
