package com.example.insurance.claimservice.controller;

import com.example.insurance.claimservice.domain.Claim;
import com.example.insurance.claimservice.dto.ApproveRequest;
import com.example.insurance.claimservice.dto.ClaimResponse;
import com.example.insurance.claimservice.dto.RejectRequest;
import com.example.insurance.claimservice.dto.RequestInfoRequest;
import com.example.insurance.claimservice.service.ApprovalService;
import com.example.insurance.claimservice.service.ClaimService;
import com.example.insurance.claimservice.service.ReviewService;
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
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * What an adjuster or admin does with a claim (PLAN 3, 7, 9.2, M6). Every call is scoped to the
 * insurer in the token, never to a value from the request, and every action is audited. An adjuster
 * cannot act on a claim they filed, and an amount above the configured threshold needs an admin.
 */
@RestController
@RequestMapping("/api/v1/claims")
@Validated
@Tag(name = "Review", description = "Staff: the review queue and the decision on a claim (M6).")
public class StaffClaimController {

  private final ReviewService reviews;
  private final ApprovalService approvals;
  private final ClaimService claims;

  public StaffClaimController(
      ReviewService reviews, ApprovalService approvals, ClaimService claims) {
    this.reviews = reviews;
    this.approvals = approvals;
    this.claims = claims;
  }

  @GetMapping("/queue")
  @SecurityRequirement(name = "bearerAuth")
  @Operation(
      summary = "The review queue",
      description =
          "Claims awaiting review for your insurer (every insurer for a platform admin), flagged"
              + " claims first, then oldest first.")
  @ApiResponse(responseCode = "200", description = "One page of claims")
  @ApiResponse(responseCode = "400", description = "Bad paging parameters (VALIDATION_FAILED)")
  @ApiResponse(responseCode = "401", description = "No valid access token (UNAUTHENTICATED)")
  @ApiResponse(responseCode = "403", description = "Not staff (FORBIDDEN, STAFF_SCOPE_MISSING)")
  public PageResponse<ClaimResponse> queue(
      @AuthenticationPrincipal Jwt jwt,
      @RequestParam(defaultValue = "0") @Min(0) int page,
      @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
    Sort order =
        Sort.by(Sort.Order.desc("flagged"), Sort.Order.asc("submittedAt"), Sort.Order.asc("id"));
    return PageResponse.of(
        reviews.queue(Actors.of(jwt), PageRequest.of(page, size, order)),
        claim -> ClaimResponse.forStaff(claim, List.of()));
  }

  @PostMapping("/{claimId}/start-review")
  @SecurityRequirement(name = "bearerAuth")
  @Operation(
      summary = "Start reviewing a claim",
      description =
          "Moves a SUBMITTED claim to UNDER_REVIEW and stores what the evaluator says is payable.")
  @ApiResponse(responseCode = "200", description = "The claim under review")
  @ApiResponse(responseCode = "401", description = "No valid access token (UNAUTHENTICATED)")
  @ApiResponse(
      responseCode = "403",
      description = "Not staff, or your own claim (FORBIDDEN, SELF_REVIEW_NOT_ALLOWED)")
  @ApiResponse(
      responseCode = "404",
      description = "No such claim at your insurer (CLAIM_NOT_FOUND)")
  @ApiResponse(responseCode = "409", description = "Not awaiting review (INVALID_TRANSITION)")
  public ClaimResponse startReview(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID claimId) {
    return staffView(reviews.startReview(Actors.of(jwt), claimId));
  }

  @PostMapping("/{claimId}/request-info")
  @SecurityRequirement(name = "bearerAuth")
  @Operation(
      summary = "Ask the customer for more information",
      description =
          "Moves a claim under review to INFO_REQUESTED and tells the customer what is needed.")
  @ApiResponse(responseCode = "200", description = "The claim, waiting for the customer")
  @ApiResponse(responseCode = "400", description = "Invalid request (VALIDATION_FAILED)")
  @ApiResponse(responseCode = "401", description = "No valid access token (UNAUTHENTICATED)")
  @ApiResponse(
      responseCode = "403",
      description = "Not staff, or your own claim (FORBIDDEN, SELF_REVIEW_NOT_ALLOWED)")
  @ApiResponse(
      responseCode = "404",
      description = "No such claim at your insurer (CLAIM_NOT_FOUND)")
  @ApiResponse(responseCode = "409", description = "Not under review (INVALID_TRANSITION)")
  public ClaimResponse requestInfo(
      @AuthenticationPrincipal Jwt jwt,
      @PathVariable UUID claimId,
      @Valid @RequestBody RequestInfoRequest request) {
    return staffView(reviews.requestInfo(Actors.of(jwt), claimId, request.message()));
  }

  @PostMapping("/{claimId}/approve")
  @SecurityRequirement(name = "bearerAuth")
  @Operation(
      summary = "Approve a claim",
      description =
          "Evaluates the policy's limits and approves for payment: the full payable amount, or a"
              + " smaller amount with a reason, never more. The approved amount is reserved against the"
              + " limits. Above the configured threshold only an admin can approve.")
  @ApiResponse(responseCode = "200", description = "The approved claim")
  @ApiResponse(responseCode = "400", description = "Invalid request (VALIDATION_FAILED)")
  @ApiResponse(responseCode = "401", description = "No valid access token (UNAUTHENTICATED)")
  @ApiResponse(
      responseCode = "403",
      description =
          "Not staff, your own claim, or above your threshold (FORBIDDEN, SELF_REVIEW_NOT_ALLOWED,"
              + " ADMIN_APPROVAL_REQUIRED)")
  @ApiResponse(
      responseCode = "404",
      description = "No such claim at your insurer (CLAIM_NOT_FOUND)")
  @ApiResponse(
      responseCode = "409",
      description = "Not under review, or limits changed at once (INVALID_TRANSITION, CONFLICT)")
  @ApiResponse(
      responseCode = "422",
      description =
          "More than payable, no reason for a partial amount, or nothing left under the limits"
              + " (APPROVAL_EXCEEDS_PAYABLE, PARTIAL_APPROVAL_REASON_REQUIRED, COUNT_LIMIT_REACHED,"
              + " LIMIT_EXHAUSTED)")
  public ClaimResponse approve(
      @AuthenticationPrincipal Jwt jwt,
      @PathVariable UUID claimId,
      @Valid @RequestBody(required = false) ApproveRequest request) {
    ApproveRequest body = request == null ? new ApproveRequest(null, null) : request;
    Claim claim = approvals.approve(Actors.of(jwt), claimId, body.approvedAmount(), body.reason());
    return staffView(claim);
  }

  @PostMapping("/{claimId}/reject")
  @SecurityRequirement(name = "bearerAuth")
  @Operation(
      summary = "Reject a claim",
      description = "Moves a claim under review to REJECTED, with a reason the customer sees.")
  @ApiResponse(responseCode = "200", description = "The rejected claim")
  @ApiResponse(responseCode = "400", description = "Invalid request (VALIDATION_FAILED)")
  @ApiResponse(responseCode = "401", description = "No valid access token (UNAUTHENTICATED)")
  @ApiResponse(
      responseCode = "403",
      description = "Not staff, or your own claim (FORBIDDEN, SELF_REVIEW_NOT_ALLOWED)")
  @ApiResponse(
      responseCode = "404",
      description = "No such claim at your insurer (CLAIM_NOT_FOUND)")
  @ApiResponse(responseCode = "409", description = "Not under review (INVALID_TRANSITION)")
  public ClaimResponse reject(
      @AuthenticationPrincipal Jwt jwt,
      @PathVariable UUID claimId,
      @Valid @RequestBody RejectRequest request) {
    return staffView(reviews.reject(Actors.of(jwt), claimId, request.reason()));
  }

  private ClaimResponse staffView(Claim claim) {
    return ClaimResponse.forStaff(claim, claims.assessmentsOf(claim.getId()));
  }
}
