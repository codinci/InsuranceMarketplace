package com.example.insurance.policyservice.policy.dto;

import com.example.insurance.policyservice.catalogue.api.Relationship;
import com.example.insurance.policyservice.catalogue.api.Sex;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDate;

@Schema(description = "A covered member.")
public record PolicyMemberView(
    @Schema(example = "ACA-000123-01") String memberNumber,
    Relationship relationship,
    Sex sex,
    String firstName,
    String lastName,
    LocalDate dateOfBirth,
    boolean student) {}
