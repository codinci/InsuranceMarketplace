package com.example.insurance.claimservice.adjudication;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import org.junit.jupiter.api.Test;

/** Policy years run from the start of cover, so the year changes on the anniversary. */
class PolicyYearsTest {

  private static final LocalDate START = LocalDate.of(2026, 12, 1);

  @Test
  void theFirstTwelveMonthsAreYearOne() {
    assertThat(PolicyYears.of(START, START)).isEqualTo(1);
    assertThat(PolicyYears.of(START, LocalDate.of(2027, 11, 30))).isEqualTo(1);
  }

  @Test
  void theAnniversaryStartsYearTwo() {
    assertThat(PolicyYears.of(START, LocalDate.of(2027, 12, 1))).isEqualTo(2);
    assertThat(PolicyYears.of(START, LocalDate.of(2029, 12, 1))).isEqualTo(4);
  }

  @Test
  void anIncidentBeforeTheStartStillCountsAsYearOne() {
    assertThat(PolicyYears.of(START, LocalDate.of(2026, 11, 1))).isEqualTo(1);
  }
}
