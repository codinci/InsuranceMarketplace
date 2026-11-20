package com.example.insurance.policyservice.catalogue.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.UUID;

@Schema(description = "An insurer.")
public record InsurerResponse(
    @Schema(description = "Insurer id. Staff tokens carry it as insurerId.") UUID id,
    @Schema(example = "ACACIA") String code,
    @Schema(example = "Acacia Assurance (fictional)") String name) {}
