package com.example.insurance.paymentservice.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.insurance.paymentservice.exception.ApiException;
import com.example.insurance.paymentservice.exception.ErrorCode;
import org.junit.jupiter.api.Test;

class IdempotencyKeysTest {

  @Test
  void aKeyOfOneToEightyCharactersIsAccepted() {
    assertThat(IdempotencyKeys.require("k")).isEqualTo("k");
    assertThat(IdempotencyKeys.require("a".repeat(80))).hasSize(80);
  }

  @Test
  void aMissingBlankOrOversizedKeyIsRefusedBeforeAnyWork() {
    for (String bad : new String[] {null, "", "   ", "a".repeat(81)}) {
      assertThatThrownBy(() -> IdempotencyKeys.require(bad))
          .isInstanceOfSatisfying(
              ApiException.class,
              ex -> assertThat(ex.code()).isEqualTo(ErrorCode.IDEMPOTENCY_KEY_REQUIRED));
    }
  }
}
