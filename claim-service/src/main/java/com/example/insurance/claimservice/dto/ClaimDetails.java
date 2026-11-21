package com.example.insurance.claimservice.dto;

import com.example.insurance.claimservice.domain.Cause;
import com.example.insurance.claimservice.domain.Claim;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * What the customer states about the claim. The server checks it against the policy at submission
 * (PLAN 9); nothing here is trusted as a decision (CLAUDE.md, the server never trusts amounts).
 */
@Schema(description = "The details of a claim, as the customer states them.")
public record ClaimDetails(
    @NotBlank @Size(max = 40) @Schema(example = "MEM-ACACIA-2026-000123-0") String memberId,
    @NotBlank @Size(max = 40) @Schema(example = "OUTPATIENT") String moduleCode,
    @NotBlank @Size(max = 40) @Schema(example = "OP-CONSULT") String benefitCode,
    @NotNull Cause cause,
    boolean surgical,
    @Size(max = 10) List<@Pattern(regexp = "^[A-Z][A-Z0-9_]{0,39}$") String> conditionTags,
    @NotNull @Schema(example = "2027-01-15") LocalDate incidentDate,
    @NotBlank @Size(max = 120) String providerName,
    @NotBlank @Size(max = 2000) String description,
    @NotNull
        @DecimalMin(value = "0.01")
        @Digits(integer = 17, fraction = 2)
        @Schema(example = "3000.00")
        BigDecimal amountClaimed) {

  /** A copy of the tags, never null: an absent list means none. */
  public ClaimDetails {
    conditionTags = conditionTags == null ? List.of() : List.copyOf(conditionTags);
  }

  /** The tags, never null: an absent list means none. */
  public List<String> tags() {
    return conditionTags;
  }

  /** The domain form, with the tags as a JSON array for storage. */
  public Claim.Details toDomain() {
    return new Claim.Details(
        memberId,
        moduleCode,
        benefitCode,
        cause,
        surgical,
        com.example.insurance.messaging.EventJson.writeValue(tags()),
        incidentDate,
        providerName,
        description,
        amountClaimed);
  }
}
