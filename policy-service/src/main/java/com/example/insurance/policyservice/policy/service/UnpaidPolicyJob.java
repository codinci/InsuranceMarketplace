package com.example.insurance.policyservice.policy.service;

import com.example.insurance.policyservice.policy.domain.PolicyStatus;
import com.example.insurance.policyservice.policy.repository.PolicyRepository;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Cancels policies left unpaid for longer than the deadline, 24 hours by default (PLAN 8.2, M4b).
 * One instance runs it; a shared lock (ShedLock) is deferred to M8 (PLAN 11). Each policy is
 * cancelled in its own transaction, so one failure does not hold up the rest.
 */
@Component
public class UnpaidPolicyJob {

  private static final Logger LOG = LoggerFactory.getLogger(UnpaidPolicyJob.class);
  private static final int BATCH_SIZE = 100;

  private final PolicyRepository policies;
  private final PolicyLifecycleService lifecycle;
  private final Clock clock;
  private final Duration deadline;

  public UnpaidPolicyJob(
      PolicyRepository policies,
      PolicyLifecycleService lifecycle,
      Clock clock,
      @Value("${policy.unpaid.deadline:PT24H}") Duration deadline) {
    this.policies = policies;
    this.lifecycle = lifecycle;
    this.clock = clock;
    this.deadline = deadline;
  }

  @Scheduled(fixedDelayString = "${policy.unpaid.check-delay-ms:60000}")
  public void run() {
    cancelOverdue(clock.instant());
  }

  /**
   * Cancels every policy that was still unpaid {@code deadline} before {@code now}. Returns how
   * many were cancelled. Tests call it with a chosen instant instead of moving the clock.
   */
  public int cancelOverdue(Instant now) {
    Instant cutoff = now.minus(deadline);
    int total = 0;
    while (true) {
      List<UUID> overdue =
          policies.findIdsByStatusCreatedBefore(
              PolicyStatus.PENDING_PAYMENT, cutoff, PageRequest.of(0, BATCH_SIZE));
      int cancelled = cancelEach(overdue);
      total += cancelled;
      // A full batch may mean more are waiting. Stop if nothing moved, so a policy that keeps
      // failing cannot loop forever.
      if (overdue.size() < BATCH_SIZE || cancelled == 0) {
        break;
      }
    }
    if (total > 0) {
      LOG.info("Cancelled {} unpaid policies.", total);
    }
    return total;
  }

  private int cancelEach(List<UUID> policyIds) {
    int cancelled = 0;
    for (UUID policyId : policyIds) {
      try {
        if (lifecycle.cancelUnpaid(policyId)) {
          cancelled++;
        }
      } catch (ObjectOptimisticLockingFailureException e) {
        // Activated by a payment at the same moment; the payment wins and the next run skips it.
        LOG.info("Policy {} changed while being cancelled; skipped.", policyId);
      }
    }
    return cancelled;
  }
}
