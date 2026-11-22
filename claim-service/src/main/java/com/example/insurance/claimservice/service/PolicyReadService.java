package com.example.insurance.claimservice.service;

import com.example.insurance.claimservice.client.PolicyClient;
import com.example.insurance.claimservice.domain.PolicySnapshot;
import com.example.insurance.claimservice.dto.PolicyView;
import com.example.insurance.claimservice.exception.ApiException;
import com.example.insurance.claimservice.exception.ErrorCode;
import com.example.insurance.claimservice.repository.PolicySnapshotRepository;
import com.example.insurance.claimservice.web.PageResponse;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The customer's view of their policies for claims (PLAN 11). The read model answers first. When
 * the policy is not there yet, policy-service is asked (the event may still be on its way). Anyone
 * who is not the holder gets 404, so the fallback never reveals another customer's policy.
 */
@Service
public class PolicyReadService {

  private final PolicySnapshotRepository snapshots;
  private final PolicyClient policyClient;

  public PolicyReadService(PolicySnapshotRepository snapshots, PolicyClient policyClient) {
    this.snapshots = snapshots;
    this.policyClient = policyClient;
  }

  @Transactional(readOnly = true)
  public PageResponse<PolicyView> list(UUID userId, Pageable pageable) {
    return PageResponse.of(snapshots.findByHolderUserId(userId, pageable), PolicyView::of);
  }

  @Transactional(readOnly = true)
  public PolicyView get(UUID userId, UUID policyId) {
    Optional<PolicySnapshot> local = findLocal(userId, policyId);
    if (local.isPresent()) {
      return PolicyView.of(local.get());
    }
    return policyClient
        .fetch(policyId)
        .filter(lookup -> userId.equals(lookup.holderUserId()))
        .map(PolicyView::of)
        .orElseThrow(() -> new ApiException(ErrorCode.POLICY_NOT_FOUND, "No such policy."));
  }

  /**
   * A policy for staff review, by id only: the claim already belongs to the staff member's insurer,
   * so no holder check applies. The read model answers first; policy-service is the fallback (PLAN
   * 11).
   */
  @Transactional(readOnly = true)
  public PolicyView getForStaff(UUID policyId) {
    return snapshots
        .findById(policyId)
        .filter(snapshot -> snapshot.getActivation() != null)
        .map(PolicyView::of)
        .or(() -> policyClient.fetch(policyId).map(PolicyView::of))
        .orElseThrow(() -> new ApiException(ErrorCode.POLICY_NOT_FOUND, "No such policy."));
  }

  private Optional<PolicySnapshot> findLocal(UUID userId, UUID policyId) {
    return snapshots.findByPolicyIdAndHolderUserId(policyId, userId);
  }
}
