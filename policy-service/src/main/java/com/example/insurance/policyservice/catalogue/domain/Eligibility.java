package com.example.insurance.policyservice.catalogue.domain;

import java.util.List;

/**
 * Who the plan accepts (PLAN 5.3). Ages are in the plan's age basis. When {@code spouseAge} is
 * absent the spouse uses the principal's range.
 */
public record Eligibility(
    List<CoverType> coverTypes,
    AgeRange principalAge,
    AgeRange spouseAge,
    ChildAge childAge,
    int maxChildren) {

  public Eligibility {
    coverTypes = coverTypes == null ? List.of() : List.copyOf(coverTypes);
  }

  public boolean allowsFamily() {
    return coverTypes.contains(CoverType.FAMILY);
  }

  public AgeRange effectiveSpouseAge() {
    return spouseAge == null ? principalAge : spouseAge;
  }

  /** The largest family the plan can cover. */
  public int maxMembers() {
    return allowsFamily() ? 2 + maxChildren : 1;
  }
}
