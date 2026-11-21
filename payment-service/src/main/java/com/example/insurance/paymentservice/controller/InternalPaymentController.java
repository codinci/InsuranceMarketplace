package com.example.insurance.paymentservice.controller;

import com.example.insurance.paymentservice.dto.CreateIntentRequest;
import com.example.insurance.paymentservice.dto.PaymentResponse;
import com.example.insurance.paymentservice.service.PaymentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Called by policy-service with a SERVICE token (ADR-014). Never routed by the gateway, so a
 * customer cannot reach it.
 */
@RestController
@RequestMapping("/internal/payments")
@Tag(
    name = "Internal",
    description =
        "Service-to-service endpoints (SERVICE token only). Not published through the gateway.")
public class InternalPaymentController {

  private final PaymentService payments;

  public InternalPaymentController(PaymentService payments) {
    this.payments = payments;
  }

  @PostMapping
  @ResponseStatus(HttpStatus.CREATED)
  @SecurityRequirement(name = "bearerAuth")
  @Operation(
      summary = "Create the premium for a policy",
      description =
          "Idempotent on the Idempotency-Key header. policy-service sends the policy id, so a retried "
              + "purchase gets the same payment back.")
  @ApiResponse(responseCode = "201", description = "Payment created, or the earlier one returned")
  @ApiResponse(
      responseCode = "400",
      description =
          "Missing Idempotency-Key (IDEMPOTENCY_KEY_REQUIRED) or invalid body (VALIDATION_FAILED)")
  @ApiResponse(responseCode = "401", description = "No valid token (UNAUTHENTICATED)")
  @ApiResponse(responseCode = "403", description = "Not a SERVICE token (FORBIDDEN)")
  @ApiResponse(
      responseCode = "409",
      description = "The key was used for a different request (IDEMPOTENCY_KEY_REUSED)")
  public PaymentResponse create(
      @RequestHeader(name = IdempotencyKeys.HEADER, required = false) String idempotencyKey,
      @Valid @RequestBody CreateIntentRequest request) {
    return payments.createIntent(IdempotencyKeys.require(idempotencyKey), request);
  }
}
