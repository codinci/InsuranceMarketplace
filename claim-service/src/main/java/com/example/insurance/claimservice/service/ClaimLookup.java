package com.example.insurance.claimservice.service;

import com.example.insurance.claimservice.domain.Claim;
import com.example.insurance.claimservice.exception.ApiException;
import com.example.insurance.claimservice.exception.ErrorCode;
import com.example.insurance.claimservice.repository.ClaimRepository;
import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * The one place that decides who may see a claim (CLAUDE.md). A customer sees only their own
 * claims. Staff see the claims of their own insurer, and a platform admin sees every insurer's.
 * Anyone else gets the same answer as for a claim that does not exist, so a claim's existence is
 * never revealed.
 */
@Component
public class ClaimLookup {

  private final ClaimRepository claims;

  public ClaimLookup(ClaimRepository claims) {
    this.claims = claims;
  }

  /** The claim for a customer or a staff member, or CLAIM_NOT_FOUND. */
  public Claim visible(Actor actor, UUID claimId) {
    return actor.isStaff() ? staffClaim(actor, claimId) : ownedBy(actor.userId(), claimId);
  }

  /** The claim for the customer who holds it, or CLAIM_NOT_FOUND. */
  public Claim ownedBy(UUID userId, UUID claimId) {
    return claims
        .findByIdAndHolderUserId(claimId, userId)
        .orElseThrow(() -> new ApiException(ErrorCode.CLAIM_NOT_FOUND, "No such claim."));
  }

  /** The claim for a staff member's insurer, or CLAIM_NOT_FOUND. A platform admin sees all. */
  public Claim staffClaim(Actor staff, UUID claimId) {
    if (staff.isPlatform()) {
      return claims
          .findById(claimId)
          .orElseThrow(() -> new ApiException(ErrorCode.CLAIM_NOT_FOUND, "No such claim."));
    }
    if (staff.insurerId() == null) {
      throw new ApiException(
          ErrorCode.STAFF_SCOPE_MISSING, "This staff account is not tied to an insurer.");
    }
    return claims
        .findByIdAndInsurerId(claimId, staff.insurerId())
        .orElseThrow(() -> new ApiException(ErrorCode.CLAIM_NOT_FOUND, "No such claim."));
  }
}
