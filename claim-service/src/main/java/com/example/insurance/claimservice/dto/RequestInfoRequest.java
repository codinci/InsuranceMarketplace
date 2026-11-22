package com.example.insurance.claimservice.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Asks the customer for more information. The message is shown to the customer. */
@Schema(description = "Asks the customer for more information about a claim.")
public record RequestInfoRequest(
    @NotBlank @Size(max = 500) @Schema(description = "What the reviewer needs.") String message) {}
