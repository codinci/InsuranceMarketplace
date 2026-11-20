package com.example.insurance.policyservice.policy.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

@Schema(description = "A policy in a list.")
public record PolicySummary(
    UUID id,
    @Schema(example = "POL-ACACIA-2026-000123") String policyNumber,
    @Schema(example = "PENDING_PAYMENT") String status,
    String insurerCode,
    String planCode,
    LocalDate startDate,
    LocalDate endDate,
    BigDecimal annualPremium,
    String currency) {}
