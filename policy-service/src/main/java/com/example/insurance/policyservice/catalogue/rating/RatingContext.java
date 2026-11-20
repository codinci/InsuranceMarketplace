package com.example.insurance.policyservice.catalogue.rating;

import com.example.insurance.policyservice.catalogue.domain.AgeBasis;
import com.example.insurance.policyservice.catalogue.domain.Relationship;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/** What a strategy needs to price one module option. */
public record RatingContext(List<RatedMember> members, LocalDate startDate, AgeBasis ageBasis) {

  public RatingContext {
    members = List.copyOf(members);
  }

  /** The member's rating age in the plan's age basis. */
  public int ageOf(RatedMember member) {
    return AgeCalculator.ratingAge(member.dateOfBirth(), startDate, ageBasis);
  }

  public Optional<RatedMember> principal() {
    return members.stream().filter(m -> m.relationship() == Relationship.PRINCIPAL).findFirst();
  }
}
