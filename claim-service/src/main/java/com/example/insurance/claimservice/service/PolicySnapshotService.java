package com.example.insurance.claimservice.service;

import com.example.insurance.claimservice.domain.PolicySnapshot;
import com.example.insurance.claimservice.repository.PolicySnapshotRepository;
import com.example.insurance.events.policy.PolicyActivated;
import com.example.insurance.events.policy.PolicyCancelled;
import com.example.insurance.events.policy.PolicyExpired;
import com.example.insurance.messaging.Inbox;
import java.time.Clock;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Applies policy events to the read model (PLAN 11). Each event is applied at most once (the inbox,
 * ADR-013) and only if it is newer than the stored version. Both checks run in the same transaction
 * as the change, so a crash cannot record an event without applying it.
 */
@Service
public class PolicySnapshotService {

  private static final Logger LOG = LoggerFactory.getLogger(PolicySnapshotService.class);

  private final PolicySnapshotRepository snapshots;
  private final Inbox inbox;
  private final Clock clock;

  public PolicySnapshotService(PolicySnapshotRepository snapshots, Inbox inbox, Clock clock) {
    this.snapshots = snapshots;
    this.inbox = inbox;
    this.clock = clock;
  }

  @Transactional
  public void onActivated(UUID eventId, UUID insurerId, PolicyActivated event, String payloadJson) {
    if (inbox.alreadyApplied(eventId)) {
      return;
    }
    PolicySnapshot snapshot =
        snapshots
            .findById(event.policyId())
            .orElseGet(
                () -> PolicySnapshot.unseen(event.policyId(), event.policyNumber(), insurerId));
    boolean applied = snapshot.applyActivated(event, insurerId, payloadJson, clock.instant());
    if (applied) {
      snapshots.save(snapshot);
    } else {
      LOG.info(
          "PolicyActivated {} for policy {} is not newer than version {}; ignored.",
          eventId,
          event.policyId(),
          snapshot.getPolicyVersion());
    }
    inbox.markApplied(eventId);
  }

  @Transactional
  public void onCancelled(UUID eventId, UUID insurerId, PolicyCancelled event) {
    if (inbox.alreadyApplied(eventId)) {
      return;
    }
    PolicySnapshot snapshot =
        snapshots
            .findById(event.policyId())
            .orElseGet(
                () -> PolicySnapshot.unseen(event.policyId(), event.policyNumber(), insurerId));
    boolean applied = snapshot.applyCancelled(event, insurerId, clock.instant());
    if (applied) {
      snapshots.save(snapshot);
    } else {
      LOG.info(
          "PolicyCancelled {} for policy {} is not newer than version {}; ignored.",
          eventId,
          event.policyId(),
          snapshot.getPolicyVersion());
    }
    inbox.markApplied(eventId);
  }

  @Transactional
  public void onExpired(UUID eventId, UUID insurerId, PolicyExpired event) {
    if (inbox.alreadyApplied(eventId)) {
      return;
    }
    PolicySnapshot snapshot =
        snapshots
            .findById(event.policyId())
            .orElseGet(
                () -> PolicySnapshot.unseen(event.policyId(), event.policyNumber(), insurerId));
    if (snapshot.applyExpired(event, insurerId, clock.instant())) {
      snapshots.save(snapshot);
    } else {
      LOG.info(
          "PolicyExpired {} for policy {} is not newer than version {}; ignored.",
          eventId,
          event.policyId(),
          snapshot.getPolicyVersion());
    }
    inbox.markApplied(eventId);
  }
}
