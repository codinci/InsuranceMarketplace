package com.example.insurance.claimservice.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;

/**
 * Approves a claim. Leave {@code approvedAmount} out to approve the full payable amount. A smaller
 * amount needs a reason; a larger one is refused. The server decides the payable amount, never the
 * client.
 */
@Schema(description = "Approves a claim for payment.")
public record ApproveRequest(
    @DecimalMin("0.01")
        @Digits(integer = 17, fraction = 2)
        @Schema(description = "Leave out to approve the full payable amount.", example = "2000.00")
        BigDecimal approvedAmount,
    @Size(max = 500) @Schema(description = "Required when approving less than the payable amount.")
        String reason) {}
