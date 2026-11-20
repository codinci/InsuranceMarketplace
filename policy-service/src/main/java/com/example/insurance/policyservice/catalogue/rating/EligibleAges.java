package com.example.insurance.policyservice.catalogue.rating;

import com.example.insurance.policyservice.catalogue.domain.AgeRange;
import com.example.insurance.policyservice.catalogue.domain.Eligibility;
import java.util.ArrayList;
import java.util.List;

/** The age ranges a rating table must cover, taken from the plan's eligibility. */
final class EligibleAges {

  private EligibleAges() {}

  /** A labelled range, so a violation can say which members are not covered. */
  record Required(String who, AgeRange range) {}

  /** The principal always; the spouse and children only when the plan sells family cover. */
  static List<Required> forAllMembers(Eligibility eligibility) {
    List<Required> ranges = new ArrayList<>();
    ranges.add(new Required("principal", eligibility.principalAge()));
    if (eligibility.allowsFamily()) {
      ranges.add(new Required("spouse", eligibility.effectiveSpouseAge()));
      if (eligibility.childAge() != null && eligibility.maxChildren() > 0) {
        ranges.add(
            new Required(
                "child",
                new AgeRange(eligibility.childAge().min(), eligibility.childAge().highestAge())));
      }
    }
    return ranges;
  }

  /** The first age in {@code range} that no covered range contains, or -1 if all are covered. */
  static int firstUncovered(AgeRange range, List<AgeRange> covered) {
    for (int age = range.min(); age <= range.max(); age++) {
      int candidate = age;
      if (covered.stream().noneMatch(c -> c.contains(candidate))) {
        return age;
      }
    }
    return -1;
  }
}
