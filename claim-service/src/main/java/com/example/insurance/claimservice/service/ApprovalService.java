package com.example.insurance.claimservice.service;

import com.example.insurance.claimservice.adjudication.ApprovalRules;
import com.example.insurance.claimservice.adjudication.LimitEvaluator;
import com.example.insurance.claimservice.adjudication.LimitEvaluator.Outcome;
import com.example.insurance.claimservice.adjudication.LimitEvaluator.RunningLimit;
import com.example.insurance.claimservice.adjudication.LimitEvaluator.Usage;
import com.example.insurance.claimservice.adjudication.PolicyYears;
import com.example.insurance.claimservice.config.ReviewProperties;
import com.example.insurance.claimservice.domain.BenefitUtilisation;
import com.example.insurance.claimservice.domain.Claim;
import com.example.insurance.claimservice.domain.ClaimStatus;
import com.example.insurance.claimservice.domain.ClaimStatusHistory;
import com.example.insurance.claimservice.dto.PolicyView;
import com.example.insurance.claimservice.exception.ApiException;
import com.example.insurance.claimservice.exception.ErrorCode;
import com.example.insurance.claimservice.messaging.Topics;
import com.example.insurance.claimservice.repository.ClaimStatusHistoryRepository;
import com.example.insurance.claimservice.rules.BenefitPath;
import com.example.insurance.claimservice.statemachine.ClaimStateMachine;
import com.example.insurance.events.EventTypes;
import com.example.insurance.events.claim.ClaimApproved;
import com.example.insurance.events.policy.TermsSnapshot.BenefitTerms;
import com.example.insurance.messaging.OutboxWriter;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionOperations;

/**
 * Approves a claim (PLAN 9.2, 11, M6). Evaluating the limits and reserving the approved amount
 * happen in one transaction. Two approvals on the same limit can read the same running total; the
 * version on the total row then lets only one of them save, and the other starts again from the new
 * total, up to {@value #MAX_ATTEMPTS} attempts. The caller sees either an approval that respects
 * every limit or a clear refusal, never a limit that was exceeded.
 */
@Service
public class ApprovalService {

  static final int MAX_ATTEMPTS = 3;
  static final String ACTION_APPROVE = "APPROVE";

  private final ClaimLookup lookup;
  private final ClaimStatusHistoryRepository history;
  private final PolicyReadService policies;
  private final UtilisationService utilisation;
  private final OutboxWriter outbox;
  private final ReviewProperties review;
  private final Clock clock;
  private final TransactionOperations transactions;
  private final StaffAudit audit;

  public ApprovalService(
      ClaimLookup lookup,
      ClaimStatusHistoryRepository history,
      PolicyReadService policies,
      UtilisationService utilisation,
      OutboxWriter outbox,
      ReviewProperties review,
      Clock clock,
      TransactionOperations transactions,
      StaffAudit audit) {
    this.lookup = lookup;
    this.history = history;
    this.policies = policies;
    this.utilisation = utilisation;
    this.outbox = outbox;
    this.review = review;
    this.clock = clock;
    this.transactions = transactions;
    this.audit = audit;
  }

  /**
   * Approves {@code requested}, or the full payable amount when it is null. Never more than the
   * evaluator allows; less needs a reason; above the threshold only an admin. Every outcome,
   * including a refusal, is written to the audit log.
   */
  public Claim approve(Actor staff, UUID claimId, BigDecimal requested, String reason) {
    return audit.run(
        staff, ACTION_APPROVE, claimId, () -> withRetry(staff, claimId, requested, reason));
  }

  private Claim withRetry(Actor staff, UUID claimId, BigDecimal requested, String reason) {
    for (int attempt = 1; ; attempt++) {
      try {
        return transactions.execute(status -> approveOnce(staff, claimId, requested, reason));
      } catch (ObjectOptimisticLockingFailureException | DataIntegrityViolationException e) {
        if (attempt >= MAX_ATTEMPTS) {
          throw new ApiException(
              ErrorCode.CONFLICT, "The limits were changed at the same time. Try again.");
        }
      }
    }
  }

  private Claim approveOnce(Actor staff, UUID claimId, BigDecimal requested, String reason) {
    Claim claim = lookup.staffClaim(staff, claimId);
    ReviewRules.requireNotOwnClaim(staff, claim);
    if (!ClaimStateMachine.canMove(claim.getStatus(), ClaimStatus.APPROVED)) {
      throw new ApiException(
          ErrorCode.INVALID_TRANSITION, "Only a claim under review can be approved.");
    }

    Adjudication adjudication = adjudicationOf(claim);
    List<BenefitUtilisation> rows =
        utilisation.rowsFor(
            adjudication.limits(),
            claim.getPolicyId(),
            adjudication.policyYear(),
            claim.getMemberId());
    Map<String, Usage> usage = new HashMap<>();
    for (int i = 0; i < rows.size(); i++) {
      usage.put(
          adjudication.limits().get(i).benefitCode(),
          new Usage(rows.get(i).getAmountUsed(), rows.get(i).getCountUsed()));
    }

    Outcome outcome =
        LimitEvaluator.evaluate(adjudication.chain(), claim.getAmountClaimed(), usage);
    if (outcome.blockedBy().isPresent()) {
      throw new ApiException(
          ErrorCode.COUNT_LIMIT_REACHED, "The limit on the number of claims has been reached.");
    }
    if (outcome.payable().signum() <= 0) {
      throw new ApiException(ErrorCode.LIMIT_EXHAUSTED, "Nothing is left to pay under the limits.");
    }
    claim.recordEligible(outcome.payable());

    BigDecimal approved =
        requested == null ? outcome.payable() : requested.setScale(2, RoundingMode.HALF_UP);
    ApprovalRules.check(
            approved, outcome.payable(), review.approvalThreshold(), staff.role(), reason)
        .ifPresent(violation -> {
          throw new ApiException(ErrorCode.valueOf(violation.code()), violation.message());
        });

    Instant now = clock.instant();
    utilisation.reserve(claim.getId(), adjudication.limits(), rows, approved);
    ClaimStatus from = claim.moveTo(ClaimStatus.APPROVED, now);
    history.save(
        new ClaimStatusHistory(
            claim.getId(),
            from,
            ClaimStatus.APPROVED,
            staff.userId(),
            staff.role(),
            "approved",
            now));
    claim.approveAmount(approved);
    claim.recordDecision(staff.userId(), staff.role(), reason, now);
    outbox.record(
        Topics.CLAIM_EVENTS,
        EventTypes.CLAIM_APPROVED,
        ClaimApproved.VERSION,
        claim.getId().toString(),
        claim.getInsurerId().toString(),
        new ClaimApproved(
            claim.getId(),
            claim.getClaimNumber(),
            claim.getPolicyId(),
            claim.getHolderUserId(),
            claim.getMemberId(),
            claim.getBenefitCode(),
            approved,
            claim.getCurrency(),
            now));
    return claim;
  }

  /**
   * What the evaluator says can be paid right now, for the reviewer. Reads the totals without
   * creating or changing anything. The authoritative evaluation happens again at approval.
   */
  public BigDecimal previewPayable(Claim claim) {
    Adjudication adjudication = adjudicationOf(claim);
    Map<String, Usage> usage =
        utilisation.peek(
            adjudication.limits(),
            claim.getPolicyId(),
            adjudication.policyYear(),
            claim.getMemberId());
    return LimitEvaluator.evaluate(adjudication.chain(), claim.getAmountClaimed(), usage).payable();
  }

  private Adjudication adjudicationOf(Claim claim) {
    PolicyView policy = policies.getForStaff(claim.getPolicyId());
    if (policy.termsSnapshot() == null || policy.coverStart() == null) {
      throw new ApiException(ErrorCode.POLICY_UNUSABLE, "The policy's terms are not available.");
    }
    List<BenefitTerms> chain =
        BenefitPath.find(policy.termsSnapshot(), claim.getModuleCode(), claim.getBenefitCode())
            .orElseThrow(() ->
                new ApiException(
                    ErrorCode.POLICY_UNUSABLE, "The benefit is not in the policy's terms."));
    return new Adjudication(
        chain,
        LimitEvaluator.runningLimits(chain),
        PolicyYears.of(policy.coverStart(), claim.getIncidentDate()));
  }

  /** The benefit chain of a claim, its running limits and the policy year it falls in. */
  private record Adjudication(
      List<BenefitTerms> chain, List<RunningLimit> limits, int policyYear) {}
}
