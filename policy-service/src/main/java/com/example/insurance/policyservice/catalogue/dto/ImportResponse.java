package com.example.insurance.policyservice.catalogue.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "The result of a plan import.")
public record ImportResponse(
    @Schema(
            description =
                "VALIDATED for a dry run, CREATED for a new draft, REPLACED when a draft was"
                    + " replaced.",
            example = "CREATED")
        String outcome,
    @Schema(description = "The stored plan version; null for a dry run.") AdminPlanResponse plan) {}
