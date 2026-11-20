package com.example.insurance.policyservice.policy.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.UUID;

@Schema(description = "Buy a policy from an open quote.")
public record PurchaseRequest(
    @NotNull UUID quoteId,
    @Schema(description = "The quoted members, in the quoted order, with names.")
        @NotEmpty
        @Size(max = 12)
        List<@Valid @NotNull PurchaseMemberRequest> members,
    @NotNull @Valid DeclarationRequest declaration) {

  public PurchaseRequest {
    members = members == null ? null : List.copyOf(members);
  }
}
