package com.example.insurance.claimservice.controller;

import com.example.insurance.claimservice.dto.PolicyView;
import com.example.insurance.claimservice.service.PolicyReadService;
import com.example.insurance.claimservice.web.PageResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.util.UUID;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** The customer's policies for claims (PLAN 3, M5a). Every call is limited to the caller's own. */
@RestController
@RequestMapping("/api/v1/claims/policies")
@Validated
@Tag(name = "Policies", description = "The customer's policies, as claims see them (M5a).")
public class PolicyController {

  private final PolicyReadService policies;

  public PolicyController(PolicyReadService policies) {
    this.policies = policies;
  }

  @GetMapping
  @SecurityRequirement(name = "bearerAuth")
  @Operation(
      summary = "List my policies",
      description =
          "The policies claim-service has received events for, newest cover first. A policy that is"
              + " still awaiting payment is not listed; GET by id can still answer for it.")
  @ApiResponse(responseCode = "200", description = "One page of policies")
  @ApiResponse(responseCode = "400", description = "Bad paging parameters (VALIDATION_FAILED)")
  @ApiResponse(responseCode = "401", description = "No valid access token (UNAUTHENTICATED)")
  @ApiResponse(responseCode = "403", description = "Not a customer (FORBIDDEN)")
  public PageResponse<PolicyView> list(
      @AuthenticationPrincipal Jwt jwt,
      @RequestParam(defaultValue = "0") @Min(0) int page,
      @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
    return policies.list(
        Customers.of(jwt),
        PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "coverStart", "policyId")));
  }

  @GetMapping("/{policyId}")
  @SecurityRequirement(name = "bearerAuth")
  @Operation(
      summary = "Read one of my policies",
      description =
          "Answers from the read model. If the event has not arrived yet, policy-service is asked."
              + " Anyone else's policy, or an unknown one, is 404.")
  @ApiResponse(responseCode = "200", description = "The policy with its terms")
  @ApiResponse(responseCode = "401", description = "No valid access token (UNAUTHENTICATED)")
  @ApiResponse(responseCode = "403", description = "Not a customer (FORBIDDEN)")
  @ApiResponse(responseCode = "404", description = "No such policy of yours (POLICY_NOT_FOUND)")
  @ApiResponse(
      responseCode = "503",
      description =
          "policy-service unavailable (POLICY_UNAVAILABLE). Retry-After tells you when to try again.")
  public PolicyView get(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID policyId) {
    return policies.get(Customers.of(jwt), policyId);
  }
}
