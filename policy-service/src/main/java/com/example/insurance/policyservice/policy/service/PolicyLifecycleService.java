package com.example.insurance.policyservice.policy.service;

import com.example.insurance.events.EventTypes;
import com.example.insurance.events.payment.PremiumFailed;
import com.example.insurance.events.payment.PremiumPaid;
import com.example.insurance.events.policy.PolicyActivated;
import com.example.insurance.events.policy.PolicyCancelled;
import com.example.insurance.events.policy.TermsSnapshot;
import com.example.insurance.messaging.Inbox;
import com.example.insurance.messaging.OutboxWriter;
import com.example.insurance.policyservice.config.BusinessCalendar;
import com.example.insurance.policyservice.policy.domain.Policy;
import com.example.insurance.policyservice.policy.domain.PolicyMember;
import com.example.insurance.policyservice.policy.messaging.Topics;
import com.example.insurance.policyservice.policy.repository.PolicyMemberRepository;
import com.example.insurance.policyservice.policy.repository.PolicyRepository;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * What happens to a policy after purchase (PLAN 7, 8.2): it activates when the premium is paid, or
 * is cancelled when it is not paid in time. Each change and its event share one transaction
 * (outbox), and each consumed event is recorded in the inbox in that same transaction, so a
 * redelivered event has no second effect (ADR-013).
 */
@Service
public class PolicyLifecycleService {

  /** The reason sent in {@code PolicyCancelled} by the unpaid-policy job. */
  public static final String REASON_UNPAID = "UNPAID";

  private static final Logger LOG = LoggerFactory.getLogger(PolicyLifecycleService.class);

  private final PolicyRepository policies;
  private final PolicyMemberRepository members;
  private final Inbox inbox;
  private final OutboxWriter outbox;
  private final BusinessCalendar calendar;

  public PolicyLifecycleService(
      PolicyRepository policies,
      PolicyMemberRepository members,
      Inbox inbox,
      OutboxWriter outbox,
      BusinessCalendar calendar) {
    this.policies = policies;
    this.members = members;
    this.inbox = inbox;
    this.outbox = outbox;
    this.calendar = calendar;
  }

  /** PENDING_PAYMENT to ACTIVE, and {@code PolicyActivated} with the frozen terms. */
  @Transactional
  public void onPremiumPaid(UUID eventId, PremiumPaid event) {
    if (inbox.alreadyApplied(eventId)) {
      return;
    }
    policies
        .findById(event.policyId())
        .ifPresentOrElse(
            policy -> activateIfPayable(policy, event),
            () -> LOG.warn("PremiumPaid {} is for an unknown policy; ignored.", eventId));
    inbox.markApplied(eventId);
  }

  /** The policy keeps waiting; the customer may pay again until the unpaid deadline. */
  @Transactional
  public void onPremiumFailed(UUID eventId, PremiumFailed event) {
    if (inbox.alreadyApplied(eventId)) {
      return;
    }
    LOG.info(
        "Premium payment {} failed ({}); policy {} still awaits payment.",
        event.paymentId(),
        event.reason(),
        event.policyId());
    inbox.markApplied(eventId);
  }

  /**
   * Cancels one unpaid policy and sends {@code PolicyCancelled(UNPAID)}. Returns false when the
   * policy is no longer awaiting payment, for example because it was paid a moment ago.
   */
  @Transactional
  public boolean cancelUnpaid(UUID policyId) {
    Policy policy = policies.findById(policyId).orElse(null);
    if (policy == null || !policy.isPendingPayment()) {
      return false;
    }
    policy.cancelUnpaid();
    outbox.record(
        Topics.POLICY_EVENTS,
        EventTypes.POLICY_CANCELLED,
        PolicyCancelled.VERSION,
        policy.getId().toString(),
        policy.getInsurerId().toString(),
        new PolicyCancelled(
            policy.getId(),
            policy.getPolicyNumber(),
            policy.getHolderUserId(),
            policy.getPolicyVersion(),
            REASON_UNPAID,
            calendar.today()));
    return true;
  }

  private void activateIfPayable(Policy policy, PremiumPaid event) {
    if (!policy.isPendingPayment()) {
      // Paid after the unpaid deadline, or a second payment. Reconciliation is M8/M9 (PLAN 11).
      LOG.warn(
          "PremiumPaid for policy {} which is {}; left unchanged.",
          policy.getId(),
          policy.getStatus());
      return;
    }
    if (!event.paymentId().equals(policy.getPaymentId())) {
      LOG.warn(
          "PremiumPaid for policy {} names payment {}, not the policy's own; ignored.",
          policy.getId(),
          event.paymentId());
      return;
    }
    policy.activate();
    outbox.record(
        Topics.POLICY_EVENTS,
        EventTypes.POLICY_ACTIVATED,
        PolicyActivated.VERSION,
        policy.getId().toString(),
        policy.getInsurerId().toString(),
        activated(policy, members.findByPolicyIdOrderByPosition(policy.getId())));
  }

  private static PolicyActivated activated(Policy policy, List<PolicyMember> policyMembers) {
    return new PolicyActivated(
        policy.getId(),
        policy.getPolicyNumber(),
        policy.getPolicyVersion(),
        policy.getHolderUserId(),
        policy.getStartDate(),
        policy.getEndDate(),
        policyMembers.stream()
            .map(m ->
                new PolicyActivated.MemberTerms(
                    m.getMemberNumber(),
                    m.getRelationship().name(),
                    m.getSex().name(),
                    m.getFirstName(),
                    m.getLastName(),
                    m.getDateOfBirth(),
                    m.isStudent()))
            .toList(),
        PolicyJson.read(policy.getTermsSnapshot(), TermsSnapshot.class));
  }
}
