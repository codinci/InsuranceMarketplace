package com.example.insurance.claimservice.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.util.UUID;

@Schema(description = "Starts a claim as a DRAFT against one of the customer's policies.")
public record CreateClaimRequest(
    @NotNull @Schema(description = "The policy the claim is against.") UUID policyId,
    @NotNull @Valid ClaimDetails claim) {}
