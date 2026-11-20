package com.example.insurance.policyservice.catalogue.domain;

/** Child ages, with an optional higher limit for students. */
public record ChildAge(int min, int max, Integer maxIfStudent, Boolean studentEvidenceRequired) {

  /** The highest age a child can have on the plan, counting the student exception. */
  public int highestAge() {
    return maxIfStudent == null ? max : Math.max(max, maxIfStudent);
  }
}
