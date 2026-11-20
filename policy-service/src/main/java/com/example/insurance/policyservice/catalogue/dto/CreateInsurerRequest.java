package com.example.insurance.policyservice.catalogue.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

@Schema(description = "A new insurer on the marketplace.")
public record CreateInsurerRequest(
    @Schema(
            description = "Upper-case code, unique on the platform. Plan documents must use it.",
            example = "MVULE")
        @NotBlank
        @Pattern(regexp = "^[A-Z0-9][A-Z0-9_-]{0,39}$")
        String code,
    @Schema(description = "Display name.", example = "Mvule Health (fictional)")
        @NotBlank
        @Size(max = 120)
        String name) {}
