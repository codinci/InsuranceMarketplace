package com.example.insurance.policyservice.policy.domain;

import com.example.insurance.policyservice.catalogue.api.Relationship;
import com.example.insurance.policyservice.catalogue.api.Sex;
import java.time.LocalDate;

/** A member as quoted. Purchase must repeat exactly these values (D36). */
public record QuotedMember(
    Relationship relationship, Sex sex, LocalDate dateOfBirth, boolean student) {}
