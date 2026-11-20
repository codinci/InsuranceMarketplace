package com.example.insurance.policyservice.catalogue.rating;

import com.example.insurance.policyservice.catalogue.domain.AgeBasis;
import com.example.insurance.policyservice.catalogue.domain.Relationship;
import com.example.insurance.policyservice.catalogue.domain.Sex;
import java.time.LocalDate;
import java.util.List;

/** Small builders for strategy tests. Start date 2027-01-01, ages AT_START. */
final class StrategyTestSupport {

  static final LocalDate START = LocalDate.of(2027, 1, 1);

  private StrategyTestSupport() {}

  /** A member who is exactly {@code age} on the start date. */
  static RatedMember member(Relationship relationship, Sex sex, int age) {
    return new RatedMember(relationship, sex, START.minusYears(age));
  }

  static RatingContext context(RatedMember... members) {
    return new RatingContext(List.of(members), START, AgeBasis.AT_START);
  }
}
