package com.example.insurance.paymentservice.service;

import com.example.insurance.events.policy.PolicyCancelled;
import com.example.insurance.messaging.Inbox;
import com.example.insurance.paymentservice.domain.Payment;
import com.example.insurance.paymentservice.domain.PaymentStatus;
import com.example.insurance.paymentservice.repository.PaymentRepository;
import java.time.Clock;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Expires the premium of a policy that was cancelled for non-payment. The inbox check and the
 * change share one transaction, so a redelivered event has no second effect (ADR-013).
 */
@Service
public class PaymentExpiryService {

  private static final Logger LOG = LoggerFactory.getLogger(PaymentExpiryService.class);

  private final PaymentRepository payments;
  private final RefundService refunds;
  private final Inbox inbox;
  private final Clock clock;

  public PaymentExpiryService(
      PaymentRepository payments, RefundService refunds, Inbox inbox, Clock clock) {
    this.payments = payments;
    this.refunds = refunds;
    this.inbox = inbox;
    this.clock = clock;
  }

  /**
   * A cancelled policy: an unpaid premium expires (M4b), and a paid one is refunded when the event
   * names a refund (M8b, ADR-021). One transaction with the inbox row, so a redelivery does
   * nothing.
   */
  @Transactional
  public void onPolicyCancelled(UUID eventId, PolicyCancelled event) {
    if (inbox.alreadyApplied(eventId)) {
      return;
    }
    payments
        .findByReferenceTypeAndReferenceId(Payment.REFERENCE_POLICY, event.policyId())
        .ifPresent(premium -> closePremium(eventId, premium, event));
    inbox.markApplied(eventId);
  }

  private void closePremium(UUID eventId, Payment premium, PolicyCancelled event) {
    if (premium.isPayable()) {
      premium.expire(clock.instant());
    } else if (premium.getStatus() == PaymentStatus.SUCCEEDED && refundDue(event)) {
      refunds.refund(eventId, premium, event);
    } else if (premium.getStatus() == PaymentStatus.SUCCEEDED
        && PolicyCancelled.REASON_UNPAID.equals(event.reason())) {
      // Paid just before the unpaid cancellation: reconciliation is parked (PLAN 11).
      LOG.warn(
          "Policy cancelled as unpaid but premium {} is {}; left unchanged.",
          premium.getId(),
          premium.getStatus());
    }
  }

  private static boolean refundDue(PolicyCancelled event) {
    return event.refundAmount() != null && event.refundAmount().signum() > 0;
  }
}
