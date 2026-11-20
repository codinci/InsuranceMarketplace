package com.example.insurance.policyservice.catalogue.api;

import java.time.LocalDate;

/** A member as the catalogue needs it to check eligibility and price. */
public record MemberInput(
    Relationship relationship, Sex sex, LocalDate dateOfBirth, boolean student) {}
