package com.example.insurance.policyservice.policy.controller;

import com.example.insurance.policyservice.policy.dto.PolicyResponse;
import com.example.insurance.policyservice.policy.dto.PolicySummary;
import com.example.insurance.policyservice.policy.dto.PurchaseRequest;
import com.example.insurance.policyservice.policy.service.PolicyService;
import com.example.insurance.policyservice.web.PageResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.util.UUID;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/policies")
@Tag(name = "Policies", description = "Buying and viewing my policies. CUSTOMER only.")
public class PolicyController {

  private final PolicyService policyService;

  public PolicyController(PolicyService policyService) {
    this.policyService = policyService;
  }

  @PostMapping
  @ResponseStatus(HttpStatus.CREATED)
  @SecurityRequirement(name = "bearerAuth")
  @Operation(
      summary = "Buy a policy from a quote",
      description =
          "Creates the policy as PENDING_PAYMENT with the quoted premium and the plan's terms"
              + " frozen, and its premium payment in payment-service. Pay it with POST"
              + " /api/v1/payments/{paymentId}/pay; the policy becomes ACTIVE once paid. Unpaid"
              + " policies are cancelled after 24 hours.")
  @ApiResponse(responseCode = "201", description = "The new policy")
  @ApiResponse(responseCode = "400", description = "Invalid request (VALIDATION_FAILED)")
  @ApiResponse(responseCode = "401", description = "No valid access token (UNAUTHENTICATED)")
  @ApiResponse(responseCode = "403", description = "Not a customer (FORBIDDEN)")
  @ApiResponse(responseCode = "404", description = "No such quote of yours (QUOTE_NOT_FOUND)")
  @ApiResponse(
      responseCode = "409",
      description = "PROFILE_REQUIRED, QUOTE_NOT_OPEN, QUOTE_EXPIRED or CONFLICT")
  @ApiResponse(
      responseCode = "422",
      description =
          "START_DATE_OUT_OF_RANGE, MEMBERS_DIFFER_FROM_QUOTE, PRINCIPAL_NOT_HOLDER or"
              + " DECLARATION_REQUIRED")
  @ApiResponse(
      responseCode = "503",
      description =
          "payment-service unavailable (PAYMENT_UNAVAILABLE). Nothing was saved; the quote stays"
              + " open, so try again.")
  public PolicyResponse purchase(
      @AuthenticationPrincipal Jwt jwt, @Valid @RequestBody PurchaseRequest request) {
    return policyService.purchase(Customers.of(jwt), request);
  }

  @GetMapping
  @SecurityRequirement(name = "bearerAuth")
  @Operation(summary = "List my policies", description = "Newest first.")
  @ApiResponse(responseCode = "200", description = "One page of policies")
  @ApiResponse(responseCode = "400", description = "Bad paging (VALIDATION_FAILED)")
  @ApiResponse(responseCode = "401", description = "No valid access token (UNAUTHENTICATED)")
  @ApiResponse(responseCode = "403", description = "Not a customer (FORBIDDEN)")
  public PageResponse<PolicySummary> list(
      @AuthenticationPrincipal Jwt jwt,
      @RequestParam(defaultValue = "0") @Min(0) int page,
      @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
    return policyService.list(
        Customers.of(jwt), PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt")));
  }

  @GetMapping("/{policyId}")
  @SecurityRequirement(name = "bearerAuth")
  @Operation(
      summary = "Get one of my policies",
      description = "With members, member numbers, the premium breakdown and the frozen terms.")
  @ApiResponse(responseCode = "200", description = "The policy")
  @ApiResponse(responseCode = "401", description = "No valid access token (UNAUTHENTICATED)")
  @ApiResponse(responseCode = "403", description = "Not a customer (FORBIDDEN)")
  @ApiResponse(responseCode = "404", description = "No such policy of yours (POLICY_NOT_FOUND)")
  public PolicyResponse get(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID policyId) {
    return policyService.get(Customers.of(jwt), policyId);
  }
}
