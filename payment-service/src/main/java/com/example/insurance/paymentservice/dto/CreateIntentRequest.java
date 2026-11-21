package com.example.insurance.paymentservice.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.util.UUID;

@Schema(
    description =
        "Sent by policy-service (SERVICE token only). The amount is the premium the server quoted; "
            + "this service never reprices it.")
public record CreateIntentRequest(
    @NotNull @Schema(description = "The policy this premium is for") UUID policyId,
    @NotBlank @Size(max = 60) @Schema(example = "POL-ACACIA-2026-000001") String policyNumber,
    @NotNull UUID insurerId,
    @NotNull @Schema(description = "The policy holder, who is the only customer who may pay")
        UUID holderUserId,
    @NotNull @DecimalMin("0.01") @Schema(example = "12500.00") BigDecimal amount,
    @NotNull @Pattern(regexp = "[A-Z]{3}") @Schema(example = "KES") String currency) {}
