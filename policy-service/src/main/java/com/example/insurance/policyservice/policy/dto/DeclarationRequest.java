package com.example.insurance.policyservice.policy.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

@Schema(description = "The health declaration (D5): no underwriting, waiting rules apply.")
public record DeclarationRequest(
    @Schema(description = "Whether any member has a pre-existing condition.") @NotNull
        Boolean preExistingConditions,
    @Schema(description = "Must be true: the customer accepts the terms.") boolean accepted) {}
