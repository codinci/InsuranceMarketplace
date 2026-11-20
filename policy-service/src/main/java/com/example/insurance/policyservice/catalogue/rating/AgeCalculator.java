package com.example.insurance.policyservice.catalogue.rating;

import com.example.insurance.policyservice.catalogue.domain.AgeBasis;
import java.time.LocalDate;
import java.time.Month;
import java.time.Year;

/**
 * Rating ages (PLAN 5.3). A member born on 29 February has their birthday on 1 March in years that
 * are not leap years (docs/catalogue-schema.md).
 */
public final class AgeCalculator {

  private AgeCalculator() {}

  /** The member's age for rating on the policy start date. */
  public static int ratingAge(LocalDate dateOfBirth, LocalDate startDate, AgeBasis basis) {
    if (dateOfBirth.isAfter(startDate)) {
      throw new RatingException(
          RatingException.DATE_OF_BIRTH_AFTER_START, "A date of birth is after the start date.");
    }
    LocalDate birthdayThisYear = birthdayIn(dateOfBirth, startDate.getYear());
    int completedYears = startDate.getYear() - dateOfBirth.getYear();
    if (birthdayThisYear.isAfter(startDate)) {
      completedYears--;
    }
    if (basis == AgeBasis.AT_START || birthdayThisYear.equals(startDate)) {
      return completedYears;
    }
    return completedYears + 1;
  }

  /** The date of the member's birthday in {@code year}. */
  static LocalDate birthdayIn(LocalDate dateOfBirth, int year) {
    boolean leapDay = dateOfBirth.getMonth() == Month.FEBRUARY && dateOfBirth.getDayOfMonth() == 29;
    if (leapDay && !Year.isLeap(year)) {
      return LocalDate.of(year, Month.MARCH, 1);
    }
    return dateOfBirth.withYear(year);
  }
}
