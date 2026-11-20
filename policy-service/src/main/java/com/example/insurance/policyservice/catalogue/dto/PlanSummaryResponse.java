package com.example.insurance.policyservice.catalogue.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Schema(description = "A plan on sale, as listed for an insurer.")
public record PlanSummaryResponse(
    @Schema(description = "Plan version id; use it for details and, from M4, quotes.") UUID id,
    @Schema(example = "ACACIA") String insurerCode,
    @Schema(example = "MEDICAL") String productCode,
    @Schema(example = "Acacia Medical") String productName,
    @Schema(example = "STANDARD") String code,
    @Schema(example = "Acacia Standard") String name,
    @Schema(example = "1") int version,
    @Schema(example = "KES") String currency,
    LocalDate saleFrom,
    @Schema(description = "Open-ended when null.") LocalDate saleTo,
    @Schema(description = "Modules and the codes of their options.") List<ModuleSummary> modules) {

  public PlanSummaryResponse {
    modules = List.copyOf(modules);
  }

  @Schema(description = "A module and its option codes.")
  public record ModuleSummary(
      @Schema(example = "OUTPATIENT") String code,
      @Schema(description = "Always included when true.") boolean mandatory,
      @Schema(example = "[\"OP-40K\", \"OP-80K\"]") List<String> options) {

    public ModuleSummary {
      options = List.copyOf(options);
    }
  }
}
