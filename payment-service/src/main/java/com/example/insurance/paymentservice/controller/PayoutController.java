package com.example.insurance.paymentservice.controller;

import com.example.insurance.paymentservice.dto.PaymentResponse;
import com.example.insurance.paymentservice.service.PayoutService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.UUID;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Claim payouts for staff (PLAN 13, M7). Only an ADMIN may reach these endpoints, and every lookup
 * is scoped by the token's {@code insurerId}: a platform admin (no insurer) sees all, and another
 * insurer's payout answers 404 like a missing one.
 */
@RestController
@RequestMapping("/api/v1/payments")
@Tag(name = "Payouts", description = "Find and retry claim payouts (admin).")
public class PayoutController {

  private final PayoutService payouts;

  public PayoutController(PayoutService payouts) {
    this.payouts = payouts;
  }

  @GetMapping("/payouts")
  @SecurityRequirement(name = "bearerAuth")
  @Operation(
      summary = "Find the payout of a claim",
      description =
          "Staff of the claim's insurer, or a platform admin. Use it to get the id to retry.")
  @ApiResponse(responseCode = "200", description = "The payout and its status")
  @ApiResponse(responseCode = "400", description = "claimId missing or not a UUID")
  @ApiResponse(responseCode = "401", description = "No valid token (UNAUTHENTICATED)")
  @ApiResponse(responseCode = "403", description = "Not an admin (FORBIDDEN)")
  @ApiResponse(
      responseCode = "404",
      description = "No payout for that claim at your insurer (PAYMENT_NOT_FOUND)")
  public PaymentResponse find(@AuthenticationPrincipal Jwt token, @RequestParam UUID claimId) {
    return payouts.findByClaim(StaffScope.insurerId(token), claimId);
  }

  @PostMapping("/{id}/retry-payout")
  @SecurityRequirement(name = "bearerAuth")
  @Operation(
      summary = "Retry a failed payout",
      description =
          "One attempt per Idempotency-Key. A repeat with the same key returns the first result. "
              + "Only a payout in FAILED can be retried. Success sends PayoutSucceeded, which moves the "
              + "claim to PAID.")
  @ApiResponse(responseCode = "200", description = "The attempt's result: SUCCEEDED or FAILED")
  @ApiResponse(
      responseCode = "400",
      description = "Missing Idempotency-Key (IDEMPOTENCY_KEY_REQUIRED)")
  @ApiResponse(responseCode = "401", description = "No valid token (UNAUTHENTICATED)")
  @ApiResponse(responseCode = "403", description = "Not an admin (FORBIDDEN)")
  @ApiResponse(
      responseCode = "404",
      description = "No such payout at your insurer (PAYMENT_NOT_FOUND)")
  @ApiResponse(
      responseCode = "409",
      description =
          "Not failed, or not a payout (PAYOUT_NOT_RETRYABLE), key reused for another payment "
              + "(IDEMPOTENCY_KEY_REUSED), or a concurrent change (CONFLICT)")
  public PaymentResponse retry(
      @AuthenticationPrincipal Jwt token,
      @PathVariable UUID id,
      @Parameter(description = "Unique per attempt, 1 to 80 characters")
          @RequestHeader(name = IdempotencyKeys.HEADER, required = false)
          String idempotencyKey) {
    return payouts.retry(StaffScope.insurerId(token), id, IdempotencyKeys.require(idempotencyKey));
  }
}
