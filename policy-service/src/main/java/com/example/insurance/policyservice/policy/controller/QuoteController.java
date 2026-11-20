package com.example.insurance.policyservice.policy.controller;

import com.example.insurance.policyservice.policy.dto.QuoteRequest;
import com.example.insurance.policyservice.policy.dto.QuoteResponse;
import com.example.insurance.policyservice.policy.service.QuoteService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/quotes")
@Tag(name = "Quotes", description = "Server-priced quotes, valid for 30 days. CUSTOMER only.")
public class QuoteController {

  private final QuoteService quoteService;

  public QuoteController(QuoteService quoteService) {
    this.quoteService = quoteService;
  }

  @PostMapping
  @ResponseStatus(HttpStatus.CREATED)
  @SecurityRequirement(name = "bearerAuth")
  @Operation(
      summary = "Get a quote",
      description =
          "Checks the start date (tomorrow up to 60 days), the plan, eligibility and the module"
              + " selection, then prices on the server.")
  @ApiResponse(responseCode = "201", description = "The quote")
  @ApiResponse(responseCode = "400", description = "Invalid request (VALIDATION_FAILED)")
  @ApiResponse(responseCode = "401", description = "No valid access token (UNAUTHENTICATED)")
  @ApiResponse(responseCode = "403", description = "Not a customer (FORBIDDEN)")
  @ApiResponse(responseCode = "404", description = "No such plan on sale (PLAN_NOT_FOUND)")
  @ApiResponse(
      responseCode = "422",
      description =
          "START_DATE_OUT_OF_RANGE, NOT_ELIGIBLE or MODULE_SELECTION_INVALID, with details in"
              + " errors")
  public QuoteResponse create(
      @AuthenticationPrincipal Jwt jwt, @Valid @RequestBody QuoteRequest request) {
    return quoteService.create(Customers.of(jwt), request);
  }

  @GetMapping("/{quoteId}")
  @SecurityRequirement(name = "bearerAuth")
  @Operation(summary = "Get one of my quotes", description = "Status is OPEN, USED or EXPIRED.")
  @ApiResponse(responseCode = "200", description = "The quote")
  @ApiResponse(responseCode = "401", description = "No valid access token (UNAUTHENTICATED)")
  @ApiResponse(responseCode = "403", description = "Not a customer (FORBIDDEN)")
  @ApiResponse(responseCode = "404", description = "No such quote of yours (QUOTE_NOT_FOUND)")
  public QuoteResponse get(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID quoteId) {
    return quoteService.get(Customers.of(jwt), quoteId);
  }
}
