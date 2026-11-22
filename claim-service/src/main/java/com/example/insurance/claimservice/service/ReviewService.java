package com.example.insurance.claimservice.service;

import com.example.insurance.claimservice.domain.Claim;
import com.example.insurance.claimservice.domain.ClaimStatus;
import com.example.insurance.claimservice.domain.ClaimStatusHistory;
import com.example.insurance.claimservice.exception.ApiException;
import com.example.insurance.claimservice.exception.ErrorCode;
import com.example.insurance.claimservice.messaging.Topics;
import com.example.insurance.claimservice.repository.ClaimRepository;
import com.example.insurance.claimservice.repository.ClaimStatusHistoryRepository;
import com.example.insurance.events.EventTypes;
import com.example.insurance.events.claim.ClaimInfoRequested;
import com.example.insurance.events.claim.ClaimRejected;
import com.example.insurance.messaging.OutboxWriter;
import java.time.Clock;
import java.time.Instant;
import java.util.EnumSet;
import java.util.Set;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The reviewer's side of a claim (PLAN 3, 7, M6): the queue, starting a review, asking the customer
 * for more information, and rejecting. Approval, which also reserves the limits, is in {@link
 * ApprovalService}. Every method starts from the staff member's own insurer, refuses a claim the
 * staff member filed, moves the status only through the state machine, writes the history, and is
 * audited.
 */
@Service
public class ReviewService {

  /** The statuses a reviewer still has work on. */
  static final Set<ClaimStatus> OPEN_STATUSES =
      EnumSet.of(ClaimStatus.SUBMITTED, ClaimStatus.UNDER_REVIEW, ClaimStatus.INFO_REQUESTED);

  private final ClaimLookup lookup;
  private final ClaimRepository claims;
  private final ClaimStatusHistoryRepository history;
  private final ApprovalService approvals;
  private final OutboxWriter outbox;
  private final Clock clock;
  private final StaffAudit audit;

  public ReviewService(
      ClaimLookup lookup,
      ClaimRepository claims,
      ClaimStatusHistoryRepository history,
      ApprovalService approvals,
      OutboxWriter outbox,
      Clock clock,
      StaffAudit audit) {
    this.lookup = lookup;
    this.claims = claims;
    this.history = history;
    this.approvals = approvals;
    this.outbox = outbox;
    this.clock = clock;
    this.audit = audit;
  }

  /**
   * The claims awaiting review for the staff member's insurer, or for every insurer for a platform
   * admin.
   */
  @Transactional(readOnly = true)
  public Page<Claim> queue(Actor staff, Pageable pageable) {
    if (staff.isPlatform()) {
      return claims.findByStatusIn(OPEN_STATUSES, pageable);
    }
    if (staff.insurerId() == null) {
      throw new ApiException(
          ErrorCode.STAFF_SCOPE_MISSING, "This staff account is not tied to an insurer.");
    }
    return claims.findByInsurerIdAndStatusIn(staff.insurerId(), OPEN_STATUSES, pageable);
  }

  /** SUBMITTED to UNDER_REVIEW. Also stores what the evaluator says is payable, as a guide. */
  @Transactional
  public Claim startReview(Actor staff, UUID claimId) {
    return audit.run(
        staff,
        "START_REVIEW",
        claimId,
        () -> {
          Claim claim = lookup.staffClaim(staff, claimId);
          ReviewRules.requireNotOwnClaim(staff, claim);
          Instant now = clock.instant();
          ClaimStatus from = claim.moveTo(ClaimStatus.UNDER_REVIEW, now);
          record(claim, from, ClaimStatus.UNDER_REVIEW, staff, "review started", now);
          claim.recordEligible(approvals.previewPayable(claim));
          claim.recordDecision(staff.userId(), staff.role(), null, now);
          return claim;
        });
  }

  /** UNDER_REVIEW to INFO_REQUESTED: the customer is asked for more, and answers with respond. */
  @Transactional
  public Claim requestInfo(Actor staff, UUID claimId, String message) {
    return audit.run(
        staff,
        "REQUEST_INFO",
        claimId,
        () -> {
          Claim claim = lookup.staffClaim(staff, claimId);
          ReviewRules.requireNotOwnClaim(staff, claim);
          Instant now = clock.instant();
          ClaimStatus from = claim.moveTo(ClaimStatus.INFO_REQUESTED, now);
          record(claim, from, ClaimStatus.INFO_REQUESTED, staff, "information requested", now);
          claim.recordDecision(staff.userId(), staff.role(), message, now);
          outbox.record(
              Topics.CLAIM_EVENTS,
              EventTypes.CLAIM_INFO_REQUESTED,
              ClaimInfoRequested.VERSION,
              claim.getId().toString(),
              claim.getInsurerId().toString(),
              new ClaimInfoRequested(
                  claim.getId(),
                  claim.getClaimNumber(),
                  claim.getPolicyId(),
                  claim.getHolderUserId(),
                  message,
                  now));
          return claim;
        });
  }

  /** UNDER_REVIEW to REJECTED with the reviewer's reason, which the customer sees. */
  @Transactional
  public Claim reject(Actor staff, UUID claimId, String reason) {
    return audit.run(
        staff,
        "REJECT",
        claimId,
        () -> {
          Claim claim = lookup.staffClaim(staff, claimId);
          ReviewRules.requireNotOwnClaim(staff, claim);
          Instant now = clock.instant();
          ClaimStatus from = claim.moveTo(ClaimStatus.REJECTED, now);
          record(claim, from, ClaimStatus.REJECTED, staff, "rejected by the reviewer", now);
          claim.rejectWith("REVIEWER_REJECTED");
          claim.recordDecision(staff.userId(), staff.role(), reason, now);
          outbox.record(
              Topics.CLAIM_EVENTS,
              EventTypes.CLAIM_REJECTED,
              ClaimRejected.VERSION,
              claim.getId().toString(),
              claim.getInsurerId().toString(),
              new ClaimRejected(
                  claim.getId(),
                  claim.getClaimNumber(),
                  claim.getPolicyId(),
                  claim.getHolderUserId(),
                  reason,
                  now));
          return claim;
        });
  }

  private void record(
      Claim claim, ClaimStatus from, ClaimStatus to, Actor staff, String reason, Instant now) {
    history.save(
        new ClaimStatusHistory(claim.getId(), from, to, staff.userId(), staff.role(), reason, now));
  }
}
