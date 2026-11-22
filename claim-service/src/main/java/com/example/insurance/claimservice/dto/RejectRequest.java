package com.example.insurance.claimservice.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Rejects a claim under review. The reason is shown to the customer. */
@Schema(description = "Rejects a claim under review.")
public record RejectRequest(
    @NotBlank @Size(max = 500) @Schema(description = "Shown to the customer.") String reason) {}
