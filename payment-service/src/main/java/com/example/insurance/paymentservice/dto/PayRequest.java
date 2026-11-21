package com.example.insurance.paymentservice.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Pattern;

@Schema(
    description = "Mock checkout options. Only for testing: the real provider has no such switch.")
public record PayRequest(
    @Pattern(regexp = "FAIL", message = "must be FAIL when given")
        @Schema(
            description =
                "MOCK ONLY. FAIL makes the mock provider decline this attempt, so the failure path "
                    + "can be exercised. Leave out for a successful payment.",
            allowableValues = {"FAIL"})
        String simulate) {}
