package com.example.insurance.policyservice.catalogue.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@Schema(description = "A plan version as staff see it.")
public record AdminPlanResponse(
    @Schema(description = "Plan version id.") UUID id,
    @Schema(description = "Owning insurer.") UUID insurerId,
    @Schema(description = "Owning product.") UUID productId,
    @Schema(example = "STANDARD") String code,
    @Schema(example = "Acacia Standard") String name,
    @Schema(description = "Plan version number.", example = "1") int version,
    @Schema(description = "DRAFT, PUBLISHED or RETIRED.", example = "DRAFT") String status,
    @Schema(example = "2026-01-01") LocalDate saleFrom,
    @Schema(description = "Open-ended when null.") LocalDate saleTo,
    @Schema(example = "KES") String currency,
    Instant createdAt,
    Instant publishedAt,
    Instant retiredAt) {}
