package com.example.insurance.policyservice.policy.service;

import com.example.insurance.events.EventTypes;
import com.example.insurance.events.policy.PolicyCancelled;
import com.example.insurance.events.policy.TermsSnapshot;
import com.example.insurance.events.policy.TermsSnapshot.CancellationTerms;
import com.example.insurance.messaging.OutboxWriter;
import com.example.insurance.policyservice.config.BusinessCalendar;
import com.example.insurance.policyservice.exception.ApiException;
import com.example.insurance.policyservice.exception.ErrorCode;
import com.example.insurance.policyservice.policy.domain.Policy;
import com.example.insurance.policyservice.policy.dto.PolicyResponse;
import com.example.insurance.policyservice.policy.messaging.Topics;
import com.example.insurance.policyservice.policy.repository.PolicyMemberRepository;
import com.example.insurance.policyservice.policy.repository.PolicyRepository;
import com.example.insurance.policyservice.policy.rules.RefundRule;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * A holder cancels their own policy (D7, ADR-021). The status, the refund and the {@code
 * PolicyCancelled} outbox row commit together; payment-service pays the refund from the event.
 */
@Service
public class PolicyCancellationService {

  private final PolicyRepository policies;
  private final PolicyMemberRepository members;
  private final ClaimCounterService claims;
  private final OutboxWriter outbox;
  private final BusinessCalendar calendar;

  public PolicyCancellationService(
      PolicyRepository policies,
      PolicyMemberRepository members,
      ClaimCounterService claims,
      OutboxWriter outbox,
      BusinessCalendar calendar) {
    this.policies = policies;
    this.members = members;
    this.claims = claims;
    this.outbox = outbox;
    this.calendar = calendar;
  }

  @Transactional
  public PolicyResponse cancel(UUID userId, UUID policyId) {
    Policy policy =
        policies
            .findByIdAndHolderUserId(policyId, userId)
            .orElseThrow(() -> new ApiException(ErrorCode.POLICY_NOT_FOUND, "No such policy."));
    LocalDate today = calendar.today();
    if (policy.isPendingPayment()) {
      policy.cancelByCustomer(today, BigDecimal.ZERO.setScale(2));
    } else if (policy.isActive()) {
      cancelActive(policy, today);
    } else {
      throw new ApiException(
          ErrorCode.POLICY_NOT_CANCELLABLE,
          "Only an active policy or one awaiting payment can be cancelled.");
    }
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
            PolicyCancelled.REASON_CUSTOMER,
            policy.getCancelEffectiveDate(),
            policy.getRefundAmount()));
    return PolicyService.toResponse(policy, members.findByPolicyIdOrderByPosition(policyId));
  }

  private void cancelActive(Policy policy, LocalDate today) {
    CancellationTerms terms =
        PolicyJson.read(policy.getTermsSnapshot(), TermsSnapshot.class).cancellation();
    int noticeDays = terms == null ? 0 : terms.noticeDays();
    LocalDate effective = today.plusDays(noticeDays);
    if (effective.isAfter(policy.getEndDate())) {
      throw new ApiException(
          ErrorCode.NOTICE_PAST_END,
          "The notice period ends after the cover does; the policy will simply expire.");
    }
    BigDecimal refund =
        RefundRule.refund(
            new RefundRule.Cancellation(
                terms,
                policy.getAnnualPremium(),
                policy.getStartDate(),
                policy.getEndDate(),
                today,
                effective,
                claims.countFor(policy.getId()),
                true));
    policy.cancelByCustomer(effective, refund);
  }
}
