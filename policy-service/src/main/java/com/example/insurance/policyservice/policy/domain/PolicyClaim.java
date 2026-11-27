package com.example.insurance.policyservice.policy.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/**
 * A claim submitted against a policy, learned from {@code ClaimSubmitted} (M8b, ADR-021). It is all
 * policy-service needs about claims: whether there were any, for the {@code noRefundIfClaims} rule.
 * claim-service stays the owner of the claim itself.
 */
@Entity
@Table(name = "policy_claims")
public class PolicyClaim {

  @Id
  @Column(name = "claim_id")
  private UUID claimId;

  @Column(name = "policy_id", nullable = false)
  private UUID policyId;

  @Column(name = "submitted_at", nullable = false)
  private Instant submittedAt;

  protected PolicyClaim() {
    // Required by JPA.
  }

  public PolicyClaim(UUID claimId, UUID policyId, Instant submittedAt) {
    this.claimId = claimId;
    this.policyId = policyId;
    this.submittedAt = submittedAt;
  }

  public UUID getClaimId() {
    return claimId;
  }

  public UUID getPolicyId() {
    return policyId;
  }

  public Instant getSubmittedAt() {
    return submittedAt;
  }
}
