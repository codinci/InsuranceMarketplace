package com.example.insurance.claimservice.service;

import com.example.insurance.claimservice.domain.Claim;
import com.example.insurance.claimservice.domain.ClaimStatus;
import com.example.insurance.claimservice.domain.ClaimStatusHistory;
import com.example.insurance.claimservice.repository.ClaimRepository;
import com.example.insurance.claimservice.repository.ClaimStatusHistoryRepository;
import com.example.insurance.claimservice.statemachine.ClaimStateMachine;
import com.example.insurance.common.Roles;
import com.example.insurance.events.payment.PayoutFailed;
import com.example.insurance.events.payment.PayoutSucceeded;
import com.example.insurance.messaging.Inbox;
import java.time.Clock;
import java.time.Instant;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Applies the payout result to the claim (PLAN 10, M7): PAID when the payout succeeded,
 * PAYOUT_FAILED when it did not. A retry that succeeds passes through PAYOUT_PENDING on its way to
 * PAID, so both steps are on the machine and in the history. The status changes only through the
 * state machine, and each event is applied at most once (the inbox, ADR-013). A limit reservation
 * is kept while the payout is failed: the money is still owed and a retry is allowed (ADR-018).
 */
@Service
public class PayoutOutcomeService {

  private static final Logger LOG = LoggerFactory.getLogger(PayoutOutcomeService.class);

  /** The actor on a change made by an event rather than by a person (history needs an actor). */
  static final UUID SYSTEM_ACTOR = new UUID(0L, 0L);

  private final ClaimRepository claims;
  private final ClaimStatusHistoryRepository history;
  private final Inbox inbox;
  private final Clock clock;

  public PayoutOutcomeService(
      ClaimRepository claims, ClaimStatusHistoryRepository history, Inbox inbox, Clock clock) {
    this.claims = claims;
    this.history = history;
    this.inbox = inbox;
    this.clock = clock;
  }

  @Transactional
  public void onPayoutSucceeded(UUID eventId, PayoutSucceeded event) {
    if (inbox.alreadyApplied(eventId)) {
      return;
    }
    Claim claim = claimOf(event.claimId());
    Instant now = clock.instant();
    if (claim.getStatus() == ClaimStatus.PAID) {
      LOG.warn("Claim {} is already PAID; event {} ignored.", claim.getId(), eventId);
    } else {
      if (claim.getStatus() == ClaimStatus.PAYOUT_FAILED) {
        move(claim, ClaimStatus.PAYOUT_PENDING, "payout retried", now);
      }
      move(claim, ClaimStatus.PAID, "payout succeeded", now);
    }
    inbox.markApplied(eventId);
  }

  @Transactional
  public void onPayoutFailed(UUID eventId, PayoutFailed event) {
    if (inbox.alreadyApplied(eventId)) {
      return;
    }
    Claim claim = claimOf(event.claimId());
    if (claim.getStatus() == ClaimStatus.PAYOUT_PENDING) {
      move(claim, ClaimStatus.PAYOUT_FAILED, "payout failed: " + event.reason(), clock.instant());
    } else {
      // Already failed (a retry that failed again) or already paid (a late event): nothing to do.
      LOG.warn(
          "Claim {} is {}; failed-payout event {} ignored.",
          claim.getId(),
          claim.getStatus(),
          eventId);
    }
    inbox.markApplied(eventId);
  }

  private Claim claimOf(UUID claimId) {
    return claims
        .findById(claimId)
        .orElseThrow(
            () -> new IllegalStateException("No claim " + claimId + " for a payout event."));
  }

  private void move(Claim claim, ClaimStatus to, String reason, Instant now) {
    if (!ClaimStateMachine.canMove(claim.getStatus(), to)) {
      throw new IllegalStateException(
          "Claim " + claim.getId() + " is " + claim.getStatus() + " and cannot become " + to + ".");
    }
    ClaimStatus from = claim.moveTo(to, now);
    history.save(
        new ClaimStatusHistory(claim.getId(), from, to, SYSTEM_ACTOR, Roles.SERVICE, reason, now));
  }
}
