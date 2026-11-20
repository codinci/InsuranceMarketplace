package com.example.insurance.policyservice.policy.dto;

import com.example.insurance.policyservice.catalogue.api.Sex;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDate;

@Schema(description = "The policy holder's details. The national ID is masked.")
public record ProfileResponse(
    String firstName,
    String lastName,
    LocalDate dateOfBirth,
    Sex sex,
    @Schema(description = "Only the last three characters.", example = "***567")
        String nationalIdMasked,
    String phone) {}
