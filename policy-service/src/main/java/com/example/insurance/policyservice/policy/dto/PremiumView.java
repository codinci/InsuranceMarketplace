package com.example.insurance.policyservice.policy.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import java.util.List;

@Schema(description = "A premium and how it was built. Every line is rounded to cents.")
public record PremiumView(
    List<ModuleLine> modules,
    BigDecimal subtotal,
    List<ChargeLine> adjustments,
    BigDecimal adjustedSubtotal,
    List<ChargeLine> levies,
    @Schema(example = "50175.00") BigDecimal annualPremium,
    @Schema(example = "KES") String currency) {

  public PremiumView {
    modules = List.copyOf(modules);
    adjustments = List.copyOf(adjustments);
    levies = List.copyOf(levies);
  }

  @Schema(description = "The premium of one selected module option.")
  public record ModuleLine(String module, String option, BigDecimal amount) {}

  @Schema(description = "One adjustment or levy.")
  public record ChargeLine(String code, BigDecimal amount) {}
}
