package com.example.insurance.common.web;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;
import org.junit.jupiter.api.Test;

class CorrelationIdsTest {

  @Test
  void keepsAWellFormedIncomingId() {
    assertThat(CorrelationIds.resolve("corr-abc.123_x")).isEqualTo("corr-abc.123_x");
  }

  @Test
  void replacesAnIdWithCharactersThatCouldForgeLogLines() {
    String forged = "ok\nFAKE LOG LINE";

    String resolved = CorrelationIds.resolve(forged);

    assertThat(resolved).isNotEqualTo(forged);
    assertThat(UUID.fromString(resolved)).isNotNull();
  }

  @Test
  void replacesAnEmptyOrMissingId() {
    assertThat(UUID.fromString(CorrelationIds.resolve(""))).isNotNull();
    assertThat(UUID.fromString(CorrelationIds.resolve(null))).isNotNull();
  }

  @Test
  void rejectsIdsLongerThan64Characters() {
    String tooLong = "a".repeat(65);

    assertThat(CorrelationIds.resolve(tooLong)).isNotEqualTo(tooLong);
    assertThat(CorrelationIds.resolve("a".repeat(64))).isEqualTo("a".repeat(64));
  }
}
