package com.example.insurance.policyservice.policy.dto;

import com.example.insurance.policyservice.catalogue.api.CoverType;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Schema(description = "What to quote. The server computes the premium; no amount is accepted.")
public record QuoteRequest(
    @Schema(description = "A plan on sale, from GET /insurers/{code}/plans.") @NotNull UUID planId,
    @NotNull CoverType coverType,
    @Schema(description = "From tomorrow up to 60 days ahead (D35).") @NotNull LocalDate startDate,
    @NotEmpty @Size(max = 12) List<@Valid @NotNull QuoteMemberRequest> members,
    @Schema(
            description = "Module code to option code. Mandatory modules must be included.",
            example = "{\"INPATIENT\": \"IP-200K\", \"OUTPATIENT\": \"OP-40K\"}")
        @NotEmpty
        @Size(max = 10)
        Map<String, String> selectedModules) {

  public QuoteRequest {
    members = members == null ? null : List.copyOf(members);
    selectedModules = selectedModules == null ? null : Map.copyOf(selectedModules);
  }
}
