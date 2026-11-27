package com.example.insurance.policyservice.policy.service;

import com.example.insurance.policyservice.config.BusinessCalendar;
import com.example.insurance.policyservice.policy.domain.PolicyStatus;
import com.example.insurance.policyservice.policy.repository.PolicyRepository;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Expires active policies the day after their last day of cover (PLAN 7, M8b). The same shape as
 * {@link UnpaidPolicyJob}: batches, one transaction per policy, and ShedLock so that with several
 * instances one runs at a time (ADR-022).
 */
@Component
public class PolicyExpiryJob {

  private static final Logger LOG = LoggerFactory.getLogger(PolicyExpiryJob.class);
  private static final int BATCH_SIZE = 100;

  private final PolicyRepository policies;
  private final PolicyLifecycleService lifecycle;
  private final BusinessCalendar calendar;

  public PolicyExpiryJob(
      PolicyRepository policies, PolicyLifecycleService lifecycle, BusinessCalendar calendar) {
    this.policies = policies;
    this.lifecycle = lifecycle;
    this.calendar = calendar;
  }

  @Scheduled(fixedDelayString = "${policy.expiry.check-delay-ms:3600000}")
  @SchedulerLock(name = "policy-expiry-job", lockAtMostFor = "PT10M", lockAtLeastFor = "PT30S")
  public void run() {
    expireDue(calendar.today());
  }

  /**
   * Expires every active policy whose end date is before {@code today}. Returns how many expired.
   * Tests call it with a chosen day instead of moving the clock.
   */
  public int expireDue(LocalDate today) {
    int total = 0;
    while (true) {
      List<UUID> due =
          policies.findIdsByStatusEndingBefore(
              PolicyStatus.ACTIVE, today, PageRequest.of(0, BATCH_SIZE));
      int expired = expireEach(due, today);
      total += expired;
      // Stop if nothing moved, so a policy that keeps failing cannot loop forever.
      if (due.size() < BATCH_SIZE || expired == 0) {
        break;
      }
    }
    if (total > 0) {
      LOG.info("Expired {} policies.", total);
    }
    return total;
  }

  private int expireEach(List<UUID> policyIds, LocalDate today) {
    int expired = 0;
    for (UUID policyId : policyIds) {
      try {
        if (lifecycle.expire(policyId, today)) {
          expired++;
        }
      } catch (ObjectOptimisticLockingFailureException e) {
        // Cancelled by its holder at the same moment; the cancellation wins.
        LOG.info("Policy {} changed while expiring; skipped.", policyId);
      }
    }
    return expired;
  }
}
