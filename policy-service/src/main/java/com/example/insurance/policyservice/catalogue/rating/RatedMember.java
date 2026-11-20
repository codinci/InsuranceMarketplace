package com.example.insurance.policyservice.catalogue.rating;

import com.example.insurance.policyservice.catalogue.domain.Relationship;
import com.example.insurance.policyservice.catalogue.domain.Sex;
import java.time.LocalDate;

/** A member as the rating engine sees it. Eligibility is checked before rating (M4). */
public record RatedMember(Relationship relationship, Sex sex, LocalDate dateOfBirth) {}
