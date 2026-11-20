package com.example.insurance.policyservice.catalogue.rating;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.insurance.policyservice.catalogue.domain.AgeBasis;
import java.time.LocalDate;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class AgeCalculatorTest {

  @ParameterizedTest(name = "born {0}, start {1}: at start {2}, next birthday {3}")
  @CsvSource({
    // birthday later in the year
    "1990-06-15, 2027-01-01, 36, 37",
    // birthday on the start date: both bases give the new age
    "1992-01-01, 2027-01-01, 35, 35",
    // birthday the day after the start date
    "1991-01-02, 2027-01-01, 35, 36",
    // birthday the day before the start date
    "1990-12-31, 2027-01-01, 36, 37",
    // born on the start date
    "2027-01-01, 2027-01-01, 0, 0",
    // a baby a few months old
    "2026-10-01, 2027-01-01, 0, 1",
    // 29 February: in a year that is not a leap year the birthday is 1 March
    "2004-02-29, 2027-02-28, 22, 23",
    "2004-02-29, 2027-03-01, 23, 23",
    "2004-02-29, 2028-02-29, 24, 24",
    "2004-02-29, 2028-02-28, 23, 24"
  })
  void ratingAges(LocalDate born, LocalDate start, int atStart, int nextBirthday) {
    assertThat(AgeCalculator.ratingAge(born, start, AgeBasis.AT_START)).isEqualTo(atStart);
    assertThat(AgeCalculator.ratingAge(born, start, AgeBasis.NEXT_BIRTHDAY))
        .isEqualTo(nextBirthday);
  }

  @ParameterizedTest
  @CsvSource({"AT_START", "NEXT_BIRTHDAY"})
  void aDateOfBirthAfterTheStartIsRefused(AgeBasis basis) {
    assertThatThrownBy(() ->
            AgeCalculator.ratingAge(LocalDate.of(2027, 1, 2), LocalDate.of(2027, 1, 1), basis))
        .isInstanceOfSatisfying(
            RatingException.class,
            e -> assertThat(e.code()).isEqualTo(RatingException.DATE_OF_BIRTH_AFTER_START));
  }
}
