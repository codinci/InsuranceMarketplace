package com.example.insurance.policyservice.policy.service;

import com.example.insurance.events.claim.ClaimSubmitted;
import com.example.insurance.messaging.Inbox;
import com.example.insurance.policyservice.policy.domain.PolicyClaim;
import com.example.insurance.policyservice.policy.repository.PolicyClaimRepository;
import com.example.insurance.policyservice.policy.repository.PolicyRepository;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Keeps the list of claims per policy from {@code claim.events}, so the refund rule never calls
 * claim-service (M8b, ADR-021). Idempotent twice: the inbox stops a repeated event, and the claim
 * id key stops the same claim announced under a new event id.
 */
@Service
public class ClaimCounterService {

  private static final Logger LOG = LoggerFactory.getLogger(ClaimCounterService.class);

  private final PolicyClaimRepository claims;
  private final PolicyRepository policies;
  private final Inbox inbox;

  public ClaimCounterService(PolicyClaimRepository claims, PolicyRepository policies, Inbox inbox) {
    this.claims = claims;
    this.policies = policies;
    this.inbox = inbox;
  }

  @Transactional
  public void onClaimSubmitted(UUID eventId, ClaimSubmitted event) {
    if (inbox.alreadyApplied(eventId)) {
      return;
    }
    if (!policies.existsById(event.policyId())) {
      LOG.warn("ClaimSubmitted {} is for an unknown policy; ignored.", eventId);
    } else if (!claims.existsById(event.claimId())) {
      claims.save(new PolicyClaim(event.claimId(), event.policyId(), event.submittedAt()));
    }
    inbox.markApplied(eventId);
  }

  /** Claims submitted against the policy that policy-service has heard of so far. */
  @Transactional(readOnly = true)
  public long countFor(UUID policyId) {
    return claims.countByPolicyId(policyId);
  }
}
