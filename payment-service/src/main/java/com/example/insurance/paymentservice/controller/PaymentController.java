package com.example.insurance.paymentservice.controller;

import com.example.insurance.paymentservice.dto.PayRequest;
import com.example.insurance.paymentservice.dto.PaymentResponse;
import com.example.insurance.paymentservice.service.PaymentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** A customer's own payments (PLAN 13). Another customer's payment answers 404 (IDOR rule). */
@RestController
@RequestMapping("/api/v1/payments")
@Tag(
    name = "Payments",
    description = "Premium payments for the caller's own policies (mock checkout).")
public class PaymentController {

  private final PaymentService payments;

  public PaymentController(PaymentService payments) {
    this.payments = payments;
  }

  @GetMapping
  @SecurityRequirement(name = "bearerAuth")
  @Operation(
      summary = "List my payments for a policy",
      description =
          "referenceId is the policy id. A policy has one premium, so the list has at most one item.")
  @ApiResponse(responseCode = "200", description = "The caller's payments for that policy")
  @ApiResponse(
      responseCode = "400",
      description = "referenceId missing or not a UUID (VALIDATION_FAILED)")
  @ApiResponse(responseCode = "401", description = "No valid token (UNAUTHENTICATED)")
  @ApiResponse(responseCode = "403", description = "Not a customer (FORBIDDEN)")
  public List<PaymentResponse> list(
      @AuthenticationPrincipal Jwt token, @RequestParam UUID referenceId) {
    return payments.listForReference(Customers.of(token), referenceId);
  }

  @GetMapping("/{id}")
  @SecurityRequirement(name = "bearerAuth")
  @Operation(summary = "Get one of my payments")
  @ApiResponse(responseCode = "200", description = "The payment and its status")
  @ApiResponse(responseCode = "401", description = "No valid token (UNAUTHENTICATED)")
  @ApiResponse(responseCode = "403", description = "Not a customer (FORBIDDEN)")
  @ApiResponse(responseCode = "404", description = "No such payment of yours (PAYMENT_NOT_FOUND)")
  public PaymentResponse get(@AuthenticationPrincipal Jwt token, @PathVariable UUID id) {
    return payments.get(Customers.of(token), id);
  }

  @PostMapping("/{id}/pay")
  @SecurityRequirement(name = "bearerAuth")
  @Operation(
      summary = "Pay a premium (mock checkout)",
      description =
          "One attempt per Idempotency-Key. A repeat with the same key returns the first result. A "
              + "failed attempt can be paid again with a new key. Success activates the policy once "
              + "policy-service consumes PremiumPaid.")
  @ApiResponse(
      responseCode = "200",
      description = "The attempt's result: SUCCEEDED, or FAILED when the mock declined it")
  @ApiResponse(
      responseCode = "400",
      description =
          "Missing Idempotency-Key (IDEMPOTENCY_KEY_REQUIRED) or invalid body (VALIDATION_FAILED)")
  @ApiResponse(responseCode = "401", description = "No valid token (UNAUTHENTICATED)")
  @ApiResponse(responseCode = "403", description = "Not a customer (FORBIDDEN)")
  @ApiResponse(responseCode = "404", description = "No such payment of yours (PAYMENT_NOT_FOUND)")
  @ApiResponse(
      responseCode = "409",
      description =
          "Already paid (PAYMENT_ALREADY_PAID), cancelled for non-payment (PAYMENT_EXPIRED), key reused "
              + "for another payment (IDEMPOTENCY_KEY_REUSED), or a concurrent change (CONFLICT)")
  public PaymentResponse pay(
      @AuthenticationPrincipal Jwt token,
      @PathVariable UUID id,
      @Parameter(description = "Unique per attempt, 1 to 80 characters")
          @RequestHeader(name = IdempotencyKeys.HEADER, required = false)
          String idempotencyKey,
      @Valid @RequestBody(required = false) PayRequest request) {
    boolean decline = request != null && "FAIL".equals(request.simulate());
    return payments.pay(Customers.of(token), id, IdempotencyKeys.require(idempotencyKey), decline);
  }
}
