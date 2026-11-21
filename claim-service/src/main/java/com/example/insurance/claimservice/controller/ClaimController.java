package com.example.insurance.claimservice.controller;

import com.example.insurance.claimservice.domain.Claim;
import com.example.insurance.claimservice.dto.ClaimDetails;
import com.example.insurance.claimservice.dto.ClaimResponse;
import com.example.insurance.claimservice.dto.CreateClaimRequest;
import com.example.insurance.claimservice.dto.StatusChangeView;
import com.example.insurance.claimservice.service.ClaimService;
import com.example.insurance.claimservice.web.PageResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** The customer's claims (PLAN 3, 7, M5b). Each call works on the caller's own claims only. */
@RestController
@RequestMapping("/api/v1/claims")
@Validated
@Tag(
    name = "Claims",
    description = "The customer's claims: draft, submit, withdraw and follow them (M5b).")
public class ClaimController {

  private final ClaimService claims;

  public ClaimController(ClaimService claims) {
    this.claims = claims;
  }

  @PostMapping
  @ResponseStatus(HttpStatus.CREATED)
  @SecurityRequirement(name = "bearerAuth")
  @Operation(
      summary = "Start a claim",
      description =
          "Creates a DRAFT against one of your policies. Nothing is checked against the policy until"
              + " you submit it.")
  @ApiResponse(responseCode = "201", description = "The draft claim")
  @ApiResponse(responseCode = "400", description = "Invalid request (VALIDATION_FAILED)")
  @ApiResponse(responseCode = "401", description = "No valid access token (UNAUTHENTICATED)")
  @ApiResponse(responseCode = "403", description = "Not a customer (FORBIDDEN)")
  @ApiResponse(responseCode = "404", description = "No such policy of yours (POLICY_NOT_FOUND)")
  @ApiResponse(
      responseCode = "409",
      description = "The policy's terms are not available (POLICY_UNUSABLE)")
  public ClaimResponse create(
      @AuthenticationPrincipal Jwt jwt, @Valid @RequestBody CreateClaimRequest request) {
    return ClaimResponse.of(claims.create(Customers.of(jwt), request), List.of());
  }

  @PutMapping("/{claimId}")
  @SecurityRequirement(name = "bearerAuth")
  @Operation(summary = "Change a draft claim", description = "Replaces the details of a draft.")
  @ApiResponse(responseCode = "200", description = "The updated claim")
  @ApiResponse(responseCode = "400", description = "Invalid request (VALIDATION_FAILED)")
  @ApiResponse(responseCode = "401", description = "No valid access token (UNAUTHENTICATED)")
  @ApiResponse(responseCode = "404", description = "No such claim of yours (CLAIM_NOT_FOUND)")
  @ApiResponse(
      responseCode = "409",
      description = "The claim is no longer a draft (CLAIM_NOT_EDITABLE)")
  public ClaimResponse update(
      @AuthenticationPrincipal Jwt jwt,
      @PathVariable UUID claimId,
      @Valid @RequestBody ClaimDetails details) {
    return ClaimResponse.of(claims.update(Customers.of(jwt), claimId, details), List.of());
  }

  @PostMapping("/{claimId}/submit")
  @SecurityRequirement(name = "bearerAuth")
  @Operation(
      summary = "Submit a claim",
      description =
          "Runs the automatic checks. A claim that passes is SUBMITTED and sent for review. A claim"
              + " that fails is REJECTED at once; the response lists each check and the reason.")
  @ApiResponse(
      responseCode = "200",
      description = "The claim, SUBMITTED or REJECTED, with its checks")
  @ApiResponse(responseCode = "401", description = "No valid access token (UNAUTHENTICATED)")
  @ApiResponse(responseCode = "404", description = "No such claim of yours (CLAIM_NOT_FOUND)")
  @ApiResponse(
      responseCode = "409",
      description = "Only a draft can be submitted (INVALID_TRANSITION, POLICY_UNUSABLE)")
  public ClaimResponse submit(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID claimId) {
    Claim claim = claims.submit(Customers.of(jwt), claimId);
    return ClaimResponse.of(claim, claims.assessmentsOf(claimId));
  }

  @PostMapping("/{claimId}/withdraw")
  @SecurityRequirement(name = "bearerAuth")
  @Operation(summary = "Withdraw a claim", description = "Withdraws a draft or a submitted claim.")
  @ApiResponse(responseCode = "200", description = "The withdrawn claim")
  @ApiResponse(responseCode = "401", description = "No valid access token (UNAUTHENTICATED)")
  @ApiResponse(responseCode = "404", description = "No such claim of yours (CLAIM_NOT_FOUND)")
  @ApiResponse(
      responseCode = "409",
      description = "The claim can no longer be withdrawn (INVALID_TRANSITION)")
  public ClaimResponse withdraw(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID claimId) {
    return ClaimResponse.of(
        claims.withdraw(Customers.of(jwt), claimId), claims.assessmentsOf(claimId));
  }

  @GetMapping
  @SecurityRequirement(name = "bearerAuth")
  @Operation(
      summary = "List my claims",
      description =
          "Your claims, newest first. The checks are shown on GET by id, not in the list.")
  @ApiResponse(responseCode = "200", description = "One page of claims")
  @ApiResponse(responseCode = "400", description = "Bad paging parameters (VALIDATION_FAILED)")
  @ApiResponse(responseCode = "401", description = "No valid access token (UNAUTHENTICATED)")
  public PageResponse<ClaimResponse> list(
      @AuthenticationPrincipal Jwt jwt,
      @RequestParam(defaultValue = "0") @Min(0) int page,
      @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
    return PageResponse.of(
        claims.list(
            Customers.of(jwt),
            PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt", "id"))),
        claim -> ClaimResponse.of(claim, List.of()));
  }

  @GetMapping("/{claimId}")
  @SecurityRequirement(name = "bearerAuth")
  @Operation(summary = "Read one of my claims", description = "The claim with its checks.")
  @ApiResponse(responseCode = "200", description = "The claim")
  @ApiResponse(responseCode = "401", description = "No valid access token (UNAUTHENTICATED)")
  @ApiResponse(responseCode = "404", description = "No such claim of yours (CLAIM_NOT_FOUND)")
  public ClaimResponse get(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID claimId) {
    Claim claim = claims.get(Customers.of(jwt), claimId);
    return ClaimResponse.of(claim, claims.assessmentsOf(claimId));
  }

  @GetMapping("/{claimId}/history")
  @SecurityRequirement(name = "bearerAuth")
  @Operation(
      summary = "Status history of my claim",
      description = "Every status change, oldest first.")
  @ApiResponse(responseCode = "200", description = "One page of status changes")
  @ApiResponse(responseCode = "401", description = "No valid access token (UNAUTHENTICATED)")
  @ApiResponse(responseCode = "404", description = "No such claim of yours (CLAIM_NOT_FOUND)")
  public PageResponse<StatusChangeView> history(
      @AuthenticationPrincipal Jwt jwt,
      @PathVariable UUID claimId,
      @RequestParam(defaultValue = "0") @Min(0) int page,
      @RequestParam(defaultValue = "50") @Min(1) @Max(100) int size) {
    return PageResponse.of(
        claims.history(
            Customers.of(jwt), claimId, PageRequest.of(page, size, Sort.by("changedAt", "id"))),
        StatusChangeView::of);
  }
}
