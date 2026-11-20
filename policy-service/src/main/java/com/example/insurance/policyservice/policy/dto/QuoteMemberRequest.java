package com.example.insurance.policyservice.policy.dto;

import com.example.insurance.policyservice.catalogue.api.Relationship;
import com.example.insurance.policyservice.catalogue.api.Sex;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Past;
import java.time.LocalDate;

@Schema(description = "A member to cover. Names are added at purchase.")
public record QuoteMemberRequest(
    @NotNull Relationship relationship,
    @NotNull Sex sex,
    @Schema(example = "1990-06-15") @NotNull @Past LocalDate dateOfBirth,
    @Schema(description = "Only for children over the plan's child age (student exception).")
        boolean student) {}
