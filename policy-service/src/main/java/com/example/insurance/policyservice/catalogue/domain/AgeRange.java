package com.example.insurance.policyservice.catalogue.domain;

/** An inclusive age range, in the plan's age basis. */
public record AgeRange(int min, int max) {

  public boolean contains(int age) {
    return age >= min && age <= max;
  }
}
