package com.example.insurance.policyservice.policy.controller;

import com.example.insurance.policyservice.policy.dto.InternalPolicyResponse;
import com.example.insurance.policyservice.policy.service.PolicyService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.UUID;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Called by claim-service when its read model has no policy yet (PLAN 11, M5a). The security rule
 * in {@code SecurityConfig} admits only the {@code claim-service} SERVICE token (ADR-014). Never
 * routed by the gateway.
 */
@RestController
@RequestMapping("/internal/policies")
@Tag(
    name = "Internal",
    description =
        "Service-to-service endpoints (SERVICE token only). Not published through the gateway.")
public class InternalPolicyController {

  private final PolicyService policies;

  public InternalPolicyController(PolicyService policies) {
    this.policies = policies;
  }

  @GetMapping("/{policyId}")
  @SecurityRequirement(name = "bearerAuth")
  @Operation(
      summary = "Read a policy for claim-service",
      description =
          "Returns the policy with its frozen terms and members. Only the claim-service token may"
              + " call it (ADR-014).")
  @ApiResponse(responseCode = "200", description = "The policy")
  @ApiResponse(responseCode = "401", description = "No valid token (UNAUTHENTICATED)")
  @ApiResponse(responseCode = "403", description = "Not the claim-service token (FORBIDDEN)")
  @ApiResponse(responseCode = "404", description = "No such policy (POLICY_NOT_FOUND)")
  public InternalPolicyResponse get(@PathVariable UUID policyId) {
    return policies.internalView(policyId);
  }
}
